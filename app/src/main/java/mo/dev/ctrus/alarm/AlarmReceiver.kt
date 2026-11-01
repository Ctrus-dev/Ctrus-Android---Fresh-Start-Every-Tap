package mo.dev.ctrus.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mo.dev.ctrus.CtrusApp

/**
 * Woken by [WakeAlarmManager]'s alarms (the main one and each spare) and by the alarm
 * notification's Snooze action. A spare that fires while the alarm is already ringing, snoozed or
 * stopped does nothing.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = CtrusApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_RING -> WakeAlarmManager.ensureRinging(app)
                    ACTION_SNOOZE -> intent.getStringExtra(EXTRA_PROFILE_ID)?.let { WakeAlarmManager.snooze(app, it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RING = "mo.dev.ctrus.alarm.RING"
        const val ACTION_SNOOZE = "mo.dev.ctrus.alarm.SNOOZE"
        const val EXTRA_PROFILE_ID = "profile_id"
    }
}
