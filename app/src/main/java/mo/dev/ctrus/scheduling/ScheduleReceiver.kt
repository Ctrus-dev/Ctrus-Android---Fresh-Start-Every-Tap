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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.MainActivity
import mo.dev.ctrus.R
import mo.dev.ctrus.strategy.StrategyIds

/**
 * Handles [ScheduleAlarmScheduler]'s alarms — the Android side of iOS's ScheduleTimerActivity
 * (automatic start and, for a fixed duration, automatic stop) and the per-weekday "Starting
 * Soon!" notification — plus re-arming every
 * schedule after a reboot, app update, or clock/time-zone change, since AlarmManager alarms
 * don't survive the first and are computed in wall-clock time for the others.
 */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = CtrusApp.from(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_START -> intent.getStringExtra(EXTRA_PROFILE_ID)?.let { onScheduledStart(context, app, it) }
                    ACTION_REMINDER -> intent.getStringExtra(EXTRA_PROFILE_ID)?.let { onReminder(context, app, it) }
                    ACTION_END -> intent.getStringExtra(EXTRA_SESSION_ID)?.let { onScheduledEnd(app, it) }
                    else -> {
                        app.scheduleAlarmScheduler.syncAll(app.profileRepository.getAll())
                        val active = app.sessionRepository.mostRecentActive()
                        val activeProfile = active?.let { app.profileRepository.find(it.profileId) }
                        if (active != null && activeProfile != null) app.scheduleAlarmScheduler.syncSessionEnd(active, activeProfile)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun onScheduledStart(context: Context, app: CtrusApp, profileId: String) = startLock.withLock {
        val profile = app.profileRepository.find(profileId) ?: return@withLock
        if (profile.blockingStrategyId != StrategyIds.SCHEDULE || profile.schedule?.isActive != true) {
            app.scheduleAlarmScheduler.cancel(profileId)
            return@withLock
        }

        val activeSession = app.sessionRepository.mostRecentActive()
        when {
            activeSession == null -> app.sessionRepository.create(profile.id, tag = StrategyIds.SCHEDULE)
            // Already running (e.g. started from the app a bit early) — keep that session.
            activeSession.profileId == profile.id -> Unit
            // A different profile is active. Never end it or swap its restrictions — skip this
            // occurrence and say so. No retry later the same day, matching iOS.
            else -> {
                val activeName = app.profileRepository.find(activeSession.profileId)?.name
                val body = if (activeName != null) {
                    context.getString(R.string.schedule_skipped_notification_body, profile.name, activeName)
                } else {
                    context.getString(R.string.schedule_skipped_notification_body_unknown, profile.name)
                }
                notify(context, "skipped:$profileId", context.getString(R.string.schedule_skipped_notification_title), body)
            }
        }
        app.scheduleAlarmScheduler.sync(profile)
    }

    /** Mirrors ScheduleTimerActivity.stop: ends the session only if it's still the one running. */
    private suspend fun onScheduledEnd(app: CtrusApp, sessionId: String) = startLock.withLock {
        val session = app.sessionRepository.find(sessionId) ?: return@withLock
        if (session.endTimeEpochMilli != null) return@withLock
        val profile = app.profileRepository.find(session.profileId) ?: return@withLock
        if (profile.blockingStrategyId != StrategyIds.SCHEDULE || profile.schedule?.durationInHours == null) return@withLock
        app.sessionRepository.endSession(session)
    }

    private suspend fun onReminder(context: Context, app: CtrusApp, profileId: String) {
        val profile = app.profileRepository.find(profileId) ?: return
        if (profile.blockingStrategyId != StrategyIds.SCHEDULE || profile.schedule?.isActive != true) {
            app.scheduleAlarmScheduler.cancel(profileId)
            return
        }
        notify(
            context,
            "reminder:$profileId",
            context.getString(R.string.schedule_reminder_notification_title),
            context.getString(R.string.schedule_reminder_notification_body, profile.name),
        )
        app.scheduleAlarmScheduler.sync(profile)
    }

    private fun notify(context: Context, key: String, title: String, body: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CtrusApp.SCHEDULE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
    }

    companion object {
        const val ACTION_START = "mo.dev.ctrus.scheduling.SCHEDULE_START"
        const val ACTION_REMINDER = "mo.dev.ctrus.scheduling.SCHEDULE_REMINDER"
        const val ACTION_END = "mo.dev.ctrus.scheduling.SCHEDULE_END"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_SESSION_ID = "session_id"

        /** Two schedules firing in the same minute must not both see "no active session". */
        private val startLock = Mutex()
    }
}
