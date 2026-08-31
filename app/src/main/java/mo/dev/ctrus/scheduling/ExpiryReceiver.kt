package mo.dev.ctrus.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mo.dev.ctrus.CtrusApp

/**
 * Fired by [AlarmSchedulingGateway] when a break's duration elapses. Directly resumes the
 * session's restrictions — the OS-triggered path equivalent to iOS's DeviceActivityMonitorExtension
 * + BreakTimerActivity.stop().
 */
class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_OWNER_ID) ?: return
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        if (intent.action != KIND_BREAK) return

        val app = CtrusApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val session = app.sessionRepository.find(sessionId) ?: return@launch
                val profile = app.profileRepository.find(profileId) ?: return@launch
                if (session.breakStartTimeEpochMilli == null || session.breakEndTimeEpochMilli != null) return@launch
                app.sessionRepository.endBreak(session, profile)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val KIND_BREAK = "mo.dev.ctrus.scheduling.BREAK"
        const val EXTRA_OWNER_ID = "owner_id"
        const val EXTRA_PROFILE_ID = "profile_id"
    }
}
