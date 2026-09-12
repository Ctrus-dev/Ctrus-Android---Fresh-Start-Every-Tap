package mo.dev.ctrus.blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
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
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
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
            .onEach { recheckForegroundApp() }
            .launchIn(serviceScope)
    }

    private fun recheckForegroundApp() {
        try {
            val packageName = rootInActiveWindow?.packageName?.toString() ?: return
            lastHandledPackage = packageName
            when (val decision = BlockDecisionEngine.decide(this, packageName)) {
                is BlockDecision.Block -> showBlocker(decision)
                BlockDecision.Dismiss -> goHome()
                BlockDecision.Allow -> Unit
            }
        } catch (e: Exception) {
            // Never let an unexpected exception here propagate: it would crash on the main
            // thread (serviceScope has no exception handler) and Android's own accessibility
            // health check disables + flags a service as "malfunctioning" after repeated crashes.
            Log.e(TAG, "recheckForegroundApp failed", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            val packageName = event?.packageName?.toString() ?: return
            if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            if (packageName == lastHandledPackage) return
            lastHandledPackage = packageName

            when (val decision = BlockDecisionEngine.decide(this, packageName)) {
                is BlockDecision.Block -> showBlocker(decision)
                BlockDecision.Dismiss -> goHome()
                BlockDecision.Allow -> Unit
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
    }
}
