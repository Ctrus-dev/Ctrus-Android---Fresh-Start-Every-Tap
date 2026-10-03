package mo.dev.ctrus.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.strategy.StrategyIds

/**
 * Android equivalent of DeviceActivityCenterUtil.scheduleTimerActivity + its
 * `scheduleUpcomingSessionReminders` for "Schedule + Ctrus NFC" profiles. Instead of one
 * repeating trigger per weekday (AlarmManager repeats aren't reliable under Doze), each profile
 * holds exactly two one-shot exact alarms — its next automatic start, and the "starts in 5
 * minutes" reminder before it — and [ScheduleReceiver] re-arms the next occurrence every time
 * one fires. A running session gets a third, per-session alarm only when the profile has a fixed
 * duration ([syncSessionEnd]). Indefinite schedules only ever stop via NFC.
 */
class ScheduleAlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Re-arms (or cancels) both alarms for every profile, and cancels any for [removedProfileIds]. */
    fun syncAll(profiles: List<BlockedProfileEntity>, removedProfileIds: Collection<String> = emptyList()) {
        removedProfileIds.forEach(::cancel)
        profiles.forEach(::sync)
    }

    fun sync(profile: BlockedProfileEntity) {
        val schedule = profile.schedule
        if (profile.blockingStrategyId != StrategyIds.SCHEDULE || schedule == null || !schedule.isActive) {
            cancel(profile.id)
            return
        }
        schedule.nextTrigger()?.let { arm(ScheduleReceiver.ACTION_START, profile.id, it.toInstant().toEpochMilli()) }
        schedule.nextTrigger(offsetMinutes = -REMINDER_MINUTES_BEFORE)?.let {
            arm(ScheduleReceiver.ACTION_REMINDER, profile.id, it.toInstant().toEpochMilli())
        }
    }

    /**
     * Arms the automatic stop for [session] when it belongs to a "Schedule + Ctrus NFC" profile
     * with a fixed duration. The stop time is start + duration, the same moment the clock counts
     * down to (see SessionTimeCalculator.expectedEndTime), whether the session started on
     * schedule or from the bubble. If that time has already passed (e.g. after a reboot), the
     * alarm fires right away.
     */
    fun syncSessionEnd(session: BlockedProfileSessionEntity, profile: BlockedProfileEntity) {
        if (profile.blockingStrategyId != StrategyIds.SCHEDULE) return
        val duration = profile.schedule?.automaticEndDurationMillis ?: return
        arm(ScheduleReceiver.ACTION_END, session.id, session.startTimeEpochMilli + duration) {
            putExtra(ScheduleReceiver.EXTRA_SESSION_ID, session.id)
        }
    }

    fun cancel(profileId: String) {
        listOf(ScheduleReceiver.ACTION_START, ScheduleReceiver.ACTION_REMINDER).forEach { action ->
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode(action, profileId),
                intent(action, profileId),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun arm(action: String, ownerId: String, triggerAtEpochMilli: Long, extras: Intent.() -> Unit = {
        putExtra(ScheduleReceiver.EXTRA_PROFILE_ID, ownerId)
    }) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(action, ownerId),
            Intent(context, ScheduleReceiver::class.java).apply { this.action = action; extras() },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // SCHEDULE_EXACT_ALARM can be revoked by the user on Android 12; an inexact alarm that
        // fires a little late beats a SecurityException that never arms anything.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMilli, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMilli, pendingIntent)
        }
    }

    // Intent.filterEquals() compares action + component (not extras), so the profile id also has
    // to live in the request code for each profile to get its own PendingIntent.
    private fun intent(action: String, profileId: String) = Intent(context, ScheduleReceiver::class.java).apply {
        this.action = action
        putExtra(ScheduleReceiver.EXTRA_PROFILE_ID, profileId)
    }

    private fun requestCode(action: String, profileId: String): Int = (action + profileId).hashCode()

    companion object {
        const val REMINDER_MINUTES_BEFORE = 5L
    }
}
