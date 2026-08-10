package mo.dev.ctrus.blocking

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Primary blocking mechanism, modeled on Switchly's SwitchlyAccessibilityService
 * (see AGENTS.md): reacts to window-state-changed events rather than polling
 * UsageStatsManager, which Switchly's own code notes is far more reliable across
 * OEMs. Currently wired to BlockDecisionEngine, which always allows until the real
 * profile/session model is ported — so this service is inert out of the box.
 */
class BlockingAccessibilityService : AccessibilityService() {
    private var lastHandledPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        Log.i(TAG, "BlockingAccessibilityService connected")
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
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "BlockingAccessibilityService interrupted")
    }

    private companion object {
        const val TAG = "BlockingA11yService"
    }
}
