package mo.dev.ctrus.blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
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
 */
class BlockingAccessibilityService : AccessibilityService() {
    private var lastHandledPackage: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
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
        val packageName = rootInActiveWindow?.packageName?.toString() ?: return
        lastHandledPackage = packageName
        when (val decision = BlockDecisionEngine.decide(this, packageName)) {
            is BlockDecision.Block -> showBlocker(decision)
            BlockDecision.Allow -> Unit
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (packageName == lastHandledPackage) return
        lastHandledPackage = packageName

        when (val decision = BlockDecisionEngine.decide(this, packageName)) {
            is BlockDecision.Block -> showBlocker(decision)
            BlockDecision.Allow -> Unit
        }
    }

    private fun showBlocker(decision: BlockDecision.Block) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val intent = Intent(this, BlockerActivity::class.java)
            .putExtra(BlockerActivity.EXTRA_PACKAGE_NAME, decision.packageName)
            .putExtra(BlockerActivity.EXTRA_PROFILE_NAME, decision.profileName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // Starting BlockerActivity in the same tick as performGlobalAction(GLOBAL_ACTION_HOME)
        // races the HOME transition: the system sometimes finishes settling on the launcher
        // *after* BlockerActivity's window is already up, which pushes BlockerActivity back off
        // screen again — the blocked app then just reads as "closed to the home screen" instead
        // of shielded. A short delay lets HOME settle first so BlockerActivity reliably ends up on
        // top.
        serviceScope.launch {
            delay(250)
            startActivity(intent)
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
    }
}
