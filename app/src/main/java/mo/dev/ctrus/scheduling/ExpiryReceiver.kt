package mo.dev.ctrus.scheduling

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.MainActivity
import mo.dev.ctrus.R

/**
 * Fired by [AlarmSchedulingGateway] for two break-related alarms: [KIND_BREAK] directly resumes
 * the session's restrictions when a break's duration elapses (the OS-triggered path equivalent to
 * iOS's DeviceActivityMonitorExtension + BreakTimerActivity.stop()), and [KIND_BREAK_WARNING]
 * posts a "break almost over" notification 60s before that, mirroring
 * StrategyManager.scheduleBreakReminder.
 */
class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            KIND_BREAK -> onBreakExpired(context, intent)
            KIND_BREAK_WARNING -> postBreakWarning(context, intent)
        }
    }

    private fun onBreakExpired(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_OWNER_ID) ?: return
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return

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

    private fun postBreakWarning(context: Context, intent: Intent) {
        val sessionId = intent.getStringExtra(EXTRA_OWNER_ID) ?: return
        val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CtrusApp.BREAK_WARNING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.break_notification_title))
            .setContentText(context.getString(R.string.break_notification_body, profileName))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(sessionId.hashCode(), notification)
    }

    companion object {
        const val KIND_BREAK = "mo.dev.ctrus.scheduling.BREAK"
        const val KIND_BREAK_WARNING = "mo.dev.ctrus.scheduling.BREAK_WARNING"
        const val EXTRA_OWNER_ID = "owner_id"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_PROFILE_NAME = "profile_name"
    }
}
