package mo.dev.ctrus.blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import mo.dev.ctrus.CtrusApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Primary blocking mechanism, modeled on Switchly's SwitchlyAccessibilityService (see
 * AGENTS.md): reacts to window-state-changed events rather than polling UsageStatsManager,
 * which Switchly's own code notes is far more reliable across OEMs.
 *
 * Shows [BlockerActivity] via startActivity(), but — mirroring Switchly's
 * BlockLaunchController.showAppBlocker()/verifyAppBlockerVisible() — never assumes that alone
 * means the block screen is actually on screen. Some OEMs (MIUI confirmed) can silently swallow
 * an Activity launch from a background service; rather than asking the user to hunt down and
 * grant a vendor-specific, inconsistently-named permission for that, this verifies shortly after
 * whether BlockerActivity actually got focus and, if not, bounces Home and retries once. This is
 * deliberately OEM-agnostic: it doesn't need to know which manufacturer or mechanism caused the
 * failure, so it should help just as much on any OEM with a similar restriction as on MIUI.
 */
class BlockingAccessibilityService : AccessibilityService() {
    private var lastHandledPackage: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lastRetryAtRealtimeByPackage = HashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Mutate the AccessibilityServiceInfo the system already built from
        // res/xml/accessibility_service_config.xml instead of replacing it with a bare instance —
        // a fresh AccessibilityServiceInfo() drops the XML's flagRetrieveInteractiveWindows /
        // canRetrieveWindowContent, which silently breaks rootInActiveWindow (used by
        // recheckForegroundApp) on many devices.
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            // Content changes are only acted on for supported browsers (address bar = the site
            // that's open); every other app's are dropped right away in onAccessibilityEvent.
            // TYPE_WINDOWS_CHANGED: split screen / pop-up windows (see checkMultiWindow).
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        Log.i(TAG, "BlockingAccessibilityService connected")

        // A profile can be started (or a break can end) while its target app is already the
        // foreground window — no fresh TYPE_WINDOW_STATE_CHANGED event fires in that case, since
        // nothing about the window itself changed, only our own blocking state did. Re-check the
        // current foreground app on every BlockingStateHolder change so an already-open app gets
        // shielded immediately instead of staying open until the next unrelated app switch.
        BlockingStateHolder.state
            .onEach { state ->
                recheckForegroundApp()
                scheduleBreakEndRecheck(state)
            }
            .launchIn(serviceScope)
    }

    private var breakEndJob: Job? = null

    /**
     * A break ends by time, not only when its alarm arrives (which can be late without "Alarms &
     * reminders"). At the scheduled end this re-shields the app that's already open and closes
     * the break in Room, so it doesn't depend on the alarm or on the user switching apps.
     */
    private fun scheduleBreakEndRecheck(state: BlockingState) {
        breakEndJob?.cancel()
        val endsAt = state.breakEndsAtEpochMilli ?: return
        val sessionId = state.sessionId ?: return
        val profileId = state.profileId ?: return
        if (!state.isBreakActive) return
        breakEndJob = serviceScope.launch {
            delay((endsAt - System.currentTimeMillis()).coerceAtLeast(0))
            recheckForegroundApp()
            val app = CtrusApp.from(this@BlockingAccessibilityService)
            app.applicationScope.launch {
                val profile = app.profileRepository.find(profileId) ?: return@launch
                app.sessionRepository.finalizeExpiredBreak(sessionId, profile)
            }
        }
    }

    private fun recheckForegroundApp() {
        try {
            val root = rootInActiveWindow ?: return
            val packageName = root.packageName?.toString() ?: return
            lastHandledPackage = packageName
            when (val decision = BlockDecisionEngine.decide(this, packageName)) {
                is BlockDecision.Block -> showBlocker(decision)
                BlockDecision.Dismiss -> goHome()
                // A session can start (or a break end) while a blocked site is already open.
                BlockDecision.Allow -> { lastCheckedHost = null; checkBrowserSite(root, packageName) }
            }
        } catch (e: Exception) {
            // Never let an unexpected exception here propagate: it would crash on the main
            // thread (serviceScope has no exception handler) and Android's own accessibility
            // health check disables + flags a service as "malfunctioning" after repeated crashes.
            Log.e(TAG, "recheckForegroundApp failed", e)
        }
    }

    // Last site checked, so the stream of content-changed events from a browser page doesn't
    // re-run the check (or relaunch the block screen) for the same site over and over.
    private var lastCheckedHost: String? = null
    private var lastBrowserCheckAtRealtime = 0L

    /** Per-site blocking: reads the open browser's address bar and blocks the site if needed. */
    private fun checkBrowserSite(root: android.view.accessibility.AccessibilityNodeInfo, packageName: String) {
        if (!BrowserUrlReader.isSupportedBrowser(packageName)) return
        if (!BlockingStateHolder.state.value.isBlocking) return
        // The active window isn't always the one with the address bar: a browser's own dialogs
        // (e.g. Chrome's "notifications make things easier" prompt) sit on top of the page. So
        // every on-screen window of that browser is searched, the active one first.
        val roots = listOf(root) + windows.mapNotNull { it.root }.filter { it.packageName?.toString() == packageName && it != root }
        val host = roots.firstNotNullOfOrNull { BrowserUrlReader.readHost(it, packageName) } ?: return
        if (host == lastCheckedHost) return
        lastCheckedHost = host
        val decision = BlockDecisionEngine.decideSite(packageName, host)
        if (decision is BlockDecision.Block) showBlocker(decision)
    }

    private var multiWindowCheckJob: Job? = null
    private val lastMultiWindowBlockAtRealtime = HashMap<String, Long>()
    private val lastHomeForMultiWindowAtRealtime = HashMap<String, Long>()
    private var lastPopupBackAtRealtime = 0L
    // Split screen / pop-up handling moves windows around for a moment; verifyBlockerVisible
    // would read that as a failed launch and add extra block screens.
    private var lastMultiWindowActionAtRealtime = 0L

    /**
     * Split screen and pop-up (freeform) windows. There, the blocked app keeps its own window and
     * BlockerActivity opens in a separate one instead of covering it, so the app stays usable
     * next to (or above) the block screen, and switching windows doesn't always fire a
     * window-state event for it. So every visible app window is checked, not just the active one.
     *
     * A blocked app counts as handled only when a full-screen Ctrus window sits above it (the
     * block screen is translucent, so the app is still listed behind it). Window bounds can't be
     * trusted on their own: MIUI reports a split-screen app at nearly full-screen size. Otherwise:
     * - it's the only app window: show the block screen;
     * - it's a pop-up the user is using (focused): press Back a few times to close it. MIUI keeps
     *   pop-ups above every full-screen window, the block screen included, and Home doesn't hide
     *   them there;
     * - first time it's next to other windows: go Home, which leaves split screen and hides
     *   pop-ups on One UI (Switchly's multiWindowBlockFallbackDetails() does the same);
     * - still split after that (MIUI keeps split screen and puts the launcher in the other half):
     *   toggle split screen off, then the app fills the screen and gets blocked. Only for
     *   full-width windows, since on a pop-up that toggle could enter split screen instead.
     * Returns true when it handled a blocked app.
     */
    private fun checkMultiWindow(followUp: Boolean = false): Boolean {
        val screen = Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val appWindows = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { window -> window.root?.packageName?.toString()?.let { AppWindow(it, window) } }
            // System UI reports app-type windows even with one full-screen app (seen on One UI).
            .filter { it.packageName != SYSTEM_UI_PACKAGE }
        // Each app's largest window: apps can own tiny helper windows too (Reddit has a 3x3 px
        // one), which would otherwise be mistaken for the app's actual window.
        val blocked = appWindows
            .groupBy { it.packageName }
            .mapNotNull { (pkg, list) ->
                (BlockDecisionEngine.decide(this, pkg) as? BlockDecision.Block)?.let { decision ->
                    decision to list.maxBy { it.bounds.width().toLong() * it.bounds.height() }
                }
            }
            .firstOrNull()
            ?: return false
        val (decision, window) = blocked
        // A pop-up minimized to its small floating icon (Home does this on One UI) is still listed
        // as the app's window, but there's nothing usable in it: acting on it re-opened the block
        // screen over the home screen again and again. Tapping the icon restores the pop-up,
        // which then has focus and gets closed below.
        if (window.isMinimized(screen)) return false
        // Split screen windows span the full width (portrait); pop-ups don't, though One UI's are
        // as wide as ~85% of the screen, so this can't share FULL_SCREEN_FRACTION.
        val coversWidth = window.bounds.width() >= screen.width() * SPLIT_WIDTH_FRACTION
        val othersVisible = appWindows.any { it.packageName != decision.packageName }

        // A pop-up being used is closed right away, without the waits below: every moment it
        // stays open is usable.
        // Alone on screen only with a clear margin: an app that's still opening can briefly be a
        // little narrower than the screen (Waze, seen on One UI), and must not get Back pressed.
        if (!coversWidth && window.focused && (othersVisible || window.isFloating(screen))) {
            Log.i(TAG, "Pop-up check: ${decision.packageName} bounds=${window.bounds} focused=${window.focused}")
            closePopupIfStill(decision)
            return true
        }
        val now = SystemClock.elapsedRealtime()

        // Just launched the block screen for it: let that land (and verifyBlockerVisible check it)
        // before reading windows mid-transition, where the launcher can still be listed.
        val sinceLaunch = SystemClock.elapsedRealtime() - (lastBlockerLaunchAtRealtime[decision.packageName] ?: 0L)
        if (!followUp && sinceLaunch < BLOCKER_SETTLE_MILLIS) return true

        // Covered by a full-screen window on a higher layer: the block screen, or the launcher
        // after Home (One UI keeps a split pair listed behind it), so nothing of it is usable.
        val covered = appWindows.any {
            it.packageName != decision.packageName && it.layer > window.layer && it.coversScreen(screen)
        }
        if (covered) return true

        val lastBlock = lastMultiWindowBlockAtRealtime[decision.packageName] ?: 0L
        if (!followUp && now - lastBlock < MULTI_WINDOW_BLOCK_COOLDOWN_MILLIS) return true
        lastMultiWindowBlockAtRealtime[decision.packageName] = now

        if (!othersVisible) {
            Log.i(TAG, "Blocked app ${decision.packageName} alone on screen (bounds=${window.bounds}), showing block screen")
            showBlocker(decision, checkSplitAfter = false)
            return true
        }

        val homeAlreadyTried = now - (lastHomeForMultiWindowAtRealtime[decision.packageName] ?: 0L) < HOME_RETRY_WINDOW_MILLIS
        when {
            !homeAlreadyTried -> {
                Log.i(TAG, "Blocked app ${decision.packageName} next to other windows (bounds=${window.bounds}, others=${appWindows.map { it.packageName }}), leaving via Home")
                lastHomeForMultiWindowAtRealtime[decision.packageName] = now
                lastMultiWindowActionAtRealtime = now
                goHome()
                // Look again as soon as Home has settled instead of waiting for the next window
                // event: if Home hid the app (One UI) there's nothing left to do; if it's still
                // split (MIUI), the toggle below runs right away.
                serviceScope.launch {
                    delay(HOME_RECHECK_DELAY_MILLIS)
                    try { checkMultiWindow(followUp = true) } catch (e: Exception) { Log.e(TAG, "checkMultiWindow failed", e) }
                }
                return true
            }
            else -> {
                // MIUI keeps split screen after Home, with the launcher in the other half. Leave
                // it, then Home again so the app (now full screen) goes away too: no block screen
                // over the home screen, the app is simply closed.
                Log.i(TAG, "Blocked app ${decision.packageName} still split after Home, toggling split screen off")
                lastMultiWindowActionAtRealtime = now
                performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                serviceScope.launch {
                    delay(MULTI_WINDOW_BLOCK_DELAY_MILLIS)
                    lastMultiWindowActionAtRealtime = SystemClock.elapsedRealtime()
                    goHome()
                }
            }
        }
        return true
    }

    /**
     * Closes a blocked app's focused pop-up by pressing Back until its window is gone (at most
     * [POPUP_BACK_PRESSES] times), stopping as soon as it closes so no extra Back reaches
     * whatever is underneath. No block screen afterwards: the app is closed, and if it's somehow
     * still there the next window check deals with it.
     */
    private fun closePopup(blockedPackage: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPopupBackAtRealtime < POPUP_BACK_PRESSES * POPUP_BACK_INTERVAL_MILLIS) return
        lastPopupBackAtRealtime = now
        lastMultiWindowActionAtRealtime = now
        Log.i(TAG, "Blocked app $blockedPackage used in a pop-up, closing it with Back")
        serviceScope.launch {
            for (i in 0 until POPUP_BACK_PRESSES) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(POPUP_BACK_INTERVAL_MILLIS)
                // Stop once the app no longer has focus, not only once its window is gone: One UI
                // can leave another of its windows behind, and further Backs then hit whatever is
                // in front.
                if (!ownsActiveWindow(blockedPackage)) break
            }
            // Still open (the app ignores Back, e.g. Waze's "exit?" prompt or games): Home hides
            // pop-ups on One UI, minimized to a floating icon (only for these apps; the rest close
            // with Back and leave no icon). On MIUI it doesn't, which is the accepted limit there.
            if (isFocusedPopup(blockedPackage)) goHome()
            lastPopupBackAtRealtime = SystemClock.elapsedRealtime()
        }
    }

    // Opened straight into a pop-up: close it now rather than putting the block screen under it
    // (MIUI keeps pop-ups above full-screen windows).
    private var popupConfirmJob: Job? = null

    /**
     * Closes the pop-up only if it still is one a moment later. An app opening from its icon is
     * reported at the size of the launch animation, a narrow window mid-screen, which looked like
     * a pop-up and got closed with Back before its block screen (WhatsApp, seen on One UI). If it
     * turned out full-screen, it gets the block screen instead.
     */
    private fun closePopupIfStill(decision: BlockDecision.Block) {
        if (popupConfirmJob?.isActive == true) return
        popupConfirmJob = serviceScope.launch {
            delay(POPUP_CONFIRM_DELAY_MILLIS)
            val pkg = decision.packageName
            when {
                isFocusedPopup(pkg) -> closePopup(pkg)
                ownsActiveWindow(pkg) && !isInSplitScreen(pkg) -> showBlocker(decision)
            }
        }
    }

    // Opened in split screen: leave it (checkMultiWindow) instead of opening the block screen in
    // the other half, which then got closed again by Home a moment later.
    private fun blockOpenedApp(decision: BlockDecision.Block) {
        when {
            isFocusedPopup(decision.packageName) -> closePopupIfStill(decision)
            isInSplitScreen(decision.packageName) -> checkMultiWindow(followUp = true)
            else -> showBlocker(decision)
        }
    }

    /** Whether [pkg] shares the screen with another app window, in a full-width but not
     *  full-height window (split screen, portrait). */
    private fun isInSplitScreen(pkg: String): Boolean {
        val screen = Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { w -> w.root?.packageName?.toString()?.let { AppWindow(it, w) } }
            .filter { it.packageName != SYSTEM_UI_PACKAGE }
        val main = appWindows.filter { it.packageName == pkg }.maxByOrNull { it.bounds.width().toLong() * it.bounds.height() } ?: return false
        return appWindows.any { it.packageName != pkg } &&
            main.bounds.width() >= screen.width() * SPLIT_WIDTH_FRACTION &&
            main.bounds.height() < screen.height() * FULL_SCREEN_FRACTION
    }

    /** Whether one of [pkg]'s app windows is the active/focused one. */
    private fun ownsActiveWindow(pkg: String): Boolean = windows.any {
        it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) && it.root?.packageName?.toString() == pkg
    }

    /** Whether the blocked app is in a pop-up the user is using right now (has focus). */
    private fun isFocusedPopup(blockedPackage: String): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        val own = appWindows.filter { it.root?.packageName?.toString() == blockedPackage }
        if (own.isEmpty()) return false
        val main = own.maxBy { w -> Rect().also(w::getBoundsInScreen).let { it.width().toLong() * it.height() } }
        val bounds = Rect().also(main::getBoundsInScreen)
        // With nothing else listed (One UI doesn't always list the launcher under a pop-up), only
        // a window with a clear margin counts, so an app still opening isn't taken for one.
        val limit = if (appWindows.size == own.size) POPUP_ALONE_WIDTH_FRACTION else SPLIT_WIDTH_FRACTION
        return bounds.width() < screenWidth * limit && (main.isFocused || main.isActive)
    }

    private class AppWindow(val packageName: String, info: AccessibilityWindowInfo) {
        val bounds = Rect().also(info::getBoundsInScreen)
        val layer = info.layer
        val focused = info.isFocused || info.isActive

        fun isFloating(screen: Rect) = bounds.width() < screen.width() * POPUP_ALONE_WIDTH_FRACTION

        fun isMinimized(screen: Rect) =
            bounds.width() < screen.width() * MINIMIZED_FRACTION && bounds.height() < screen.height() * MINIMIZED_FRACTION

        fun coversScreen(screen: Rect) =
            bounds.width() >= screen.width() * FULL_SCREEN_FRACTION && bounds.height() >= screen.height() * FULL_SCREEN_FRACTION
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
                // These come in bursts while windows move/resize: check once it settles.
                multiWindowCheckJob?.cancel()
                if (BlockingStateHolder.state.value.isBlocking) {
                    multiWindowCheckJob = serviceScope.launch {
                        delay(MULTI_WINDOW_CHECK_DEBOUNCE_MILLIS)
                        try { checkMultiWindow() } catch (e: Exception) { Log.e(TAG, "checkMultiWindow failed", e) }
                    }
                }
                return
            }
            val packageName = event?.packageName?.toString() ?: return
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                // Fires constantly while a page loads/scrolls: only browsers matter, and at
                // most a few checks per second.
                if (!BrowserUrlReader.isSupportedBrowser(packageName)) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastBrowserCheckAtRealtime < BROWSER_CHECK_INTERVAL_MILLIS) return
                lastBrowserCheckAtRealtime = now
                val root = rootInActiveWindow ?: return
                if (root.packageName?.toString() != packageName) return
                checkBrowserSite(root, packageName)
                return
            }
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            // Coming back to a browser re-checks its site even if it's the same one as before
            // (the block screen was dismissed, but the tab is still on that site).
            if (BrowserUrlReader.isSupportedBrowser(packageName)) lastCheckedHost = null
            if (packageName == lastHandledPackage) return
            lastHandledPackage = packageName

            when (val decision = BlockDecisionEngine.decide(this, packageName)) {
                is BlockDecision.Block -> when {
                    ownsActiveWindow(packageName) -> blockOpenedApp(decision)
                    // Apps also fire this while leaving the screen (seen on One UI: under the
                    // block screen after OK, or a pop-up being closed), which re-showed the block
                    // screen over the home screen. Look again once things have settled.
                    else -> serviceScope.launch {
                        delay(INACTIVE_RECHECK_DELAY_MILLIS)
                        val again = BlockDecisionEngine.decide(this@BlockingAccessibilityService, packageName)
                        if (again is BlockDecision.Block && ownsActiveWindow(packageName)) blockOpenedApp(again)
                    }
                }
                BlockDecision.Dismiss -> goHome()
                BlockDecision.Allow -> rootInActiveWindow?.let { checkBrowserSite(it, packageName) }
            }
        } catch (e: Exception) {
            // See recheckForegroundApp()'s comment — an accessibility event callback must never
            // throw uncaught, or the OS can disable this service as "malfunctioning".
            Log.e(TAG, "onAccessibilityEvent failed", e)
        }
    }

    /** Leaves the Play Store / uninstall-confirmation surface without the citrus shield screen.
     *  performGlobalAction is the same privileged, accessibility-only "press the Home button"
     *  Switchly uses (BlockLaunchController.postHome()) — more reliable across OEMs than crafting
     *  an ACTION_MAIN/CATEGORY_HOME intent, which some OEM launchers/system wrappers can refuse
     *  or intercept. */
    private fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * [checkSplitAfter]: in split screen the block screen opens in the other half, leaving the
     * blocked app usable, and no further window event may come to notice it. So once it has
     * settled, the windows are checked again (checkMultiWindow does nothing when a full-screen
     * block screen covers the app). Off when called from checkMultiWindow itself.
     */
    private fun showBlocker(decision: BlockDecision.Block, checkSplitAfter: Boolean = true) {
        // Already on screen for this app: launching again only pauses/resumes it for nothing.
        if (BlockerActivity.isShowingFor(decision.packageName) && isOwnWindowFocused()) return
        // Two paths can ask for the same block screen at once (a window event and a window
        // check); one launch is enough.
        val launchedAt = SystemClock.elapsedRealtime()
        if (launchedAt - (lastBlockerLaunchAtRealtime[decision.packageName] ?: 0L) < DUPLICATE_LAUNCH_MILLIS) return
        launchBlocker(decision)
        serviceScope.launch {
            delay(VERIFY_DELAY_MILLIS)
            verifyBlockerVisible(decision, launchedAt)
        }
        if (checkSplitAfter) {
            serviceScope.launch {
                delay(BLOCKER_SETTLE_MILLIS)
                try { checkMultiWindow(followUp = true) } catch (e: Exception) { Log.e(TAG, "checkMultiWindow failed", e) }
            }
        }
    }

    /** Whether a Ctrus window (the block screen) has focus right now, read from the window list
     *  rather than BlockerActivity's own flags, which an instance that's finishing can clear
     *  after the next one has already taken focus. */
    /** Whether the topmost app window is Ctrus's own and fills the screen, whatever has focus
     *  (focus can sit elsewhere for a moment, e.g. with the keyboard still open). */
    private fun isOwnWindowOnTop(): Boolean {
        val screen = Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val top = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer } ?: return false
        return top.root?.packageName?.toString() == packageName &&
            AppWindow(packageName, top).coversScreen(screen)
    }

    private fun isOwnWindowFocused(): Boolean =
        windows.any { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused && it.root?.packageName?.toString() == packageName }

    /** Launched immediately, on top of the blocked app, with no performGlobalAction(HOME) kick
     *  first — that used to flash the launcher for ~250ms before BlockerActivity came up, which
     *  read as the blocked app abruptly closing. BlockerActivity's own dismiss button goes home
     *  itself (see its kdoc), so the blocked app's task is never revealed either way; this just
     *  removes the visible detour through the home screen on the way in. The extra flags mirror
     *  Switchly's BlockerActivity.show(): NO_ANIMATION/NO_USER_ACTION skip transition/afterimage
     *  behavior that can otherwise let the blocked app redraw a frame first, CLEAR_TOP ensures a
     *  second launch (the visibility retry below) reuses/updates the same instance rather than
     *  stacking a duplicate. */
    private val lastBlockerLaunchAtRealtime = HashMap<String, Long>()

    private fun launchBlocker(decision: BlockDecision.Block) {
        lastBlockerLaunchAtRealtime[decision.packageName] = SystemClock.elapsedRealtime()
        val intent = Intent(this, BlockerActivity::class.java)
            .putExtra(BlockerActivity.EXTRA_PACKAGE_NAME, decision.packageName)
            .putExtra(BlockerActivity.EXTRA_PROFILE_NAME, decision.profileName)
            .putExtra(BlockerActivity.EXTRA_SITE_DOMAIN, decision.siteDomain)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        startActivity(intent)
    }

    /** Mirrors Switchly's BlockLaunchController.verifyAppBlockerVisible(): confirms the block
     *  screen actually got window focus rather than trusting that startActivity() alone means
     *  it's showing — some OEMs (MIUI confirmed) can silently swallow that call. If it's not
     *  visible and this package hasn't already been retried within the cooldown window, bounce
     *  Home first (their comment: "some OEMs keep the previous app or an app-filter surface in
     *  front even though the Activity launch succeeded — bouncing Home first makes the retry much
     *  more likely to get a real focused window") and relaunch once. */
    private fun verifyBlockerVisible(decision: BlockDecision.Block, launchedAt: Long) {
        // It did show and was already dismissed with OK (quick taps, seen on One UI): retrying
        // here re-opened it over the home screen.
        if (BlockerActivity.wasShownSince(decision.packageName, launchedAt)) return
        if (BlockerActivity.isRecentlyFocusedFor(decision.packageName) || BlockerActivity.isShowingFor(decision.packageName)) return
        // The block screen can have focus while BlockerActivity's flags say otherwise (see
        // isOwnWindowFocused); bouncing Home then flashed the home screen for nothing.
        if (isOwnWindowFocused() || isOwnWindowOnTop()) return
        if (SystemClock.elapsedRealtime() - lastMultiWindowActionAtRealtime < MULTI_WINDOW_VERIFY_PAUSE_MILLIS) return

        val now = SystemClock.elapsedRealtime()
        val lastRetry = lastRetryAtRealtimeByPackage[decision.packageName] ?: 0L
        if (now - lastRetry < RETRY_COOLDOWN_MILLIS) {
            Log.w(TAG, "Block screen not visible for ${decision.packageName}, retry throttled")
            return
        }
        lastRetryAtRealtimeByPackage[decision.packageName] = now
        Log.w(TAG, "Block screen not visible for ${decision.packageName} (manufacturer=${android.os.Build.MANUFACTURER}), retrying via Home bounce; windows=" +
            windows.joinToString { "${it.root?.packageName}:t${it.type}:l${it.layer}:f${it.isFocused}:a${it.isActive}" })

        goHome()
        serviceScope.launch {
            delay(RETRY_DELAY_MILLIS)
            launchBlocker(decision)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "BlockingAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private companion object {
        const val TAG = "BlockingA11yService"
        const val VERIFY_DELAY_MILLIS = 750L
        const val RETRY_DELAY_MILLIS = 140L
        const val RETRY_COOLDOWN_MILLIS = 2_500L
        const val BROWSER_CHECK_INTERVAL_MILLIS = 300L
        const val MULTI_WINDOW_CHECK_DEBOUNCE_MILLIS = 100L
        const val MULTI_WINDOW_VERIFY_PAUSE_MILLIS = 2_000L
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        // A window this close to the screen size counts as full screen (apps that don't draw
        // behind the status/navigation bars are a bit shorter than the display).
        const val FULL_SCREEN_FRACTION = 0.85f
        const val SPLIT_WIDTH_FRACTION = 0.97f
        // One UI pop-ups are ~85% of the screen width, MIUI's ~70%.
        const val POPUP_ALONE_WIDTH_FRACTION = 0.9f
        // Longer than an app's launch animation.
        const val POPUP_CONFIRM_DELAY_MILLIS = 400L
        const val DUPLICATE_LAUNCH_MILLIS = 300L
        // Smaller than this in both directions: a minimized pop-up's floating icon, not a window
        // anyone can use (resized pop-ups stay well above it).
        const val MINIMIZED_FRACTION = 0.3f
        // If the blocked app is still in part of the screen this soon after going Home, Home
        // didn't leave split screen (MIUI).
        const val HOME_RETRY_WINDOW_MILLIS = 4_000L
        // Enough Backs to leave most apps' inner screens and close the pop-up itself.
        const val POPUP_BACK_PRESSES = 4
        const val POPUP_BACK_INTERVAL_MILLIS = 120L
        const val BLOCKER_SETTLE_MILLIS = 800L
        const val INACTIVE_RECHECK_DELAY_MILLIS = 300L
        const val HOME_RECHECK_DELAY_MILLIS = 350L
        // Long enough for the Home transition, so the block screen opens full-screen afterwards.
        const val MULTI_WINDOW_BLOCK_DELAY_MILLIS = 240L
        const val MULTI_WINDOW_BLOCK_COOLDOWN_MILLIS = 1_000L
    }
}
