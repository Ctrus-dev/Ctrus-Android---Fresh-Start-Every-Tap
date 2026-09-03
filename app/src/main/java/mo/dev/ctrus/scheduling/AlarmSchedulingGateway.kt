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
        cancel(requestCode(ExpiryReceiver.KIND_BREAK, sessionId))
    }

    override fun scheduleBreakWarning(sessionId: String, profileName: String, triggerAtEpochMilli: Long) {
        val intent = Intent(context, ExpiryReceiver::class.java).apply {
            action = ExpiryReceiver.KIND_BREAK_WARNING
            putExtra(ExpiryReceiver.EXTRA_OWNER_ID, sessionId)
            putExtra(ExpiryReceiver.EXTRA_PROFILE_NAME, profileName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(ExpiryReceiver.KIND_BREAK_WARNING, sessionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMilli, pendingIntent)
    }

    override fun cancelBreakWarning(sessionId: String) {
        cancel(requestCode(ExpiryReceiver.KIND_BREAK_WARNING, sessionId))
    }

    private fun cancel(requestCode: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
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
            requestCode(ExpiryReceiver.KIND_BREAK, sessionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(kind: String, sessionId: String): Int = (kind + sessionId).hashCode()
}
