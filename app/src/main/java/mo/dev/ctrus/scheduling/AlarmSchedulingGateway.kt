package mo.dev.ctrus.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * AlarmManager-backed [SchedulingGateway] for break auto-resume — the Android equivalent of
 * iOS's BreakTimerActivity. `setExactAndAllowWhileIdle` is used since this is a user-visible,
 * time-sensitive expiration (a break that doesn't actually end on time is a real bug).
 */
class AlarmSchedulingGateway(private val context: Context) : SchedulingGateway {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun scheduleBreakExpiry(sessionId: String, profileId: String, triggerAtEpochMilli: Long) {
        val pendingIntent = pendingIntent(sessionId, profileId)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMilli, pendingIntent)
    }

    override fun cancelBreakExpiry(sessionId: String) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(sessionId),
            Intent(context, ExpiryReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(sessionId: String, profileId: String): PendingIntent {
        val intent = Intent(context, ExpiryReceiver::class.java).apply {
            action = ExpiryReceiver.KIND_BREAK
            putExtra(ExpiryReceiver.EXTRA_OWNER_ID, sessionId)
            putExtra(ExpiryReceiver.EXTRA_PROFILE_ID, profileId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(sessionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(sessionId: String): Int = (ExpiryReceiver.KIND_BREAK + sessionId).hashCode()
}
