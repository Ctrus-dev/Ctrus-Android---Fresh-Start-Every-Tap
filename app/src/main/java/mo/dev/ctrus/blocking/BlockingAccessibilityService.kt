package mo.dev.ctrus.blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
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
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
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
                is BlockDecision.Block -> showBlocker(decision)
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

    private fun showBlocker(decision: BlockDecision.Block) {
        launchBlocker(decision)
        serviceScope.launch {
            delay(VERIFY_DELAY_MILLIS)
            verifyBlockerVisible(decision)
        }
    }

    /** Launched immediately, on top of the blocked app, with no performGlobalAction(HOME) kick
     *  first — that used to flash the launcher for ~250ms before BlockerActivity came up, which
     *  read as the blocked app abruptly closing. BlockerActivity's own dismiss button goes home
     *  itself (see its kdoc), so the blocked app's task is never revealed either way; this just
     *  removes the visible detour through the home screen on the way in. The extra flags mirror
     *  Switchly's BlockerActivity.show(): NO_ANIMATION/NO_USER_ACTION skip transition/afterimage
     *  behavior that can otherwise let the blocked app redraw a frame first, CLEAR_TOP ensures a
     *  second launch (the visibility retry below) reuses/updates the same instance rather than
     *  stacking a duplicate. */
    private fun launchBlocker(decision: BlockDecision.Block) {
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
    private fun verifyBlockerVisible(decision: BlockDecision.Block) {
        if (BlockerActivity.isRecentlyFocusedFor(decision.packageName)) return

        val now = SystemClock.elapsedRealtime()
        val lastRetry = lastRetryAtRealtimeByPackage[decision.packageName] ?: 0L
        if (now - lastRetry < RETRY_COOLDOWN_MILLIS) {
            Log.w(TAG, "Block screen not visible for ${decision.packageName}, retry throttled")
            return
        }
        lastRetryAtRealtimeByPackage[decision.packageName] = now
        Log.w(TAG, "Block screen not visible for ${decision.packageName} (manufacturer=${android.os.Build.MANUFACTURER}), retrying via Home bounce")

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
    }
}
