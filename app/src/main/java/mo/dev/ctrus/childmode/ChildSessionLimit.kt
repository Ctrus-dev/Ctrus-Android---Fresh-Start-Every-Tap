package mo.dev.ctrus.childmode

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.scheduling.ScheduleAlarmScheduler
import mo.dev.ctrus.scheduling.ScheduleReceiver
import mo.dev.ctrus.scheduling.setWhileIdleBestEffort
import mo.dev.ctrus.strategy.StrategyIds
import mo.dev.ctrus.util.TrustedClock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Port of iOS's ChildSessionLimit — Child Mode's automatic end (spec Part B, 8.3). Every session
 * stops after 24 hours at most, or sooner for a schedule's fixed duration, or 5 minutes before the
 * same schedule's next session (which then starts on time with its own 24 hours).
 *
 * An exact alarm wakes [ScheduleReceiver] when the end is due. Alarms follow the phone's date, so
 * before ending anything [check] adds up the real time since the session started with
 * [TrustedClock]; if it hasn't actually passed, the alarm is set again for what's left. The app
 * also checks on open, on boot and on date changes.
 */
object ChildSessionLimit {
    const val MAX_SESSION_MILLIS = 24L * 60 * 60 * 1000
    private val REMINDER_LEAD_MILLIS = ScheduleAlarmScheduler.REMINDER_MINUTES_BEFORE * 60_000
    /** The alarm can fire marginally early. */
    private const val SLACK_MILLIS = 5_000L

    private const val KEY_SESSION_ID = "childSessionId"
    private const val KEY_ELAPSED = "childSessionElapsed"
    private const val REQUEST_CODE = 0x4368_4c64

    data class Plan(
        /** Real time the session may run for, counted from its start. */
        val limitMillis: Long,
        /** It ends to make way for the same schedule's next session, so the reminder shows as it ends. */
        val makesWayForNextSession: Boolean,
    )

    private val lock = Mutex()

    private fun prefs(context: Context) = context.getSharedPreferences("mo.dev.ctrus.child_session", Context.MODE_PRIVATE)
    private fun clock(context: Context) = TrustedClock("childSession", TrustedClock.SharedPreferencesStore(context))

    /**
     * Brings the automatic end in line with the active session: starts counting for a new one,
     * checks a known one, and drops it once no session is running or Child Mode is off. Called
     * whenever the active session changes, on app open, on boot and from the alarm.
     */
    suspend fun sync(app: CtrusApp) = lock.withLock {
        val prefs = prefs(app)
        val session = app.sessionRepository.mostRecentActive()
        if (!ChildModeStore.isChildMode || session == null) {
            cancelAlarm(app)
            prefs.edit().clear().commit()
            return@withLock
        }
        val profile = app.profileRepository.find(session.profileId) ?: return@withLock

        if (prefs.getString(KEY_SESSION_ID, null) != session.id) {
            // Reading the clock here resets it, so only time from now on is counted.
            clock(app).trustedElapsedMillis()
            prefs.edit().putString(KEY_SESSION_ID, session.id).putLong(KEY_ELAPSED, 0).commit()
            armAlarm(app, plan(profile, Instant.ofEpochMilli(session.startTimeEpochMilli)).limitMillis)
            return@withLock
        }
        check(app, session, profile)
    }

    private suspend fun check(app: CtrusApp, session: BlockedProfileSessionEntity, profile: BlockedProfileEntity) {
        ChildModeStore.checkForClockChange()

        val prefs = prefs(app)
        val elapsed = prefs.getLong(KEY_ELAPSED, 0) + clock(app).trustedElapsedMillis()
        prefs.edit().putLong(KEY_ELAPSED, elapsed).commit()

        val plan = plan(profile, Instant.ofEpochMilli(session.startTimeEpochMilli))
        val remaining = plan.limitMillis - elapsed
        if (remaining > SLACK_MILLIS) {
            armAlarm(app, remaining)
            return
        }

        app.schedulingGateway.cancelBreakExpiry(session.id)
        app.schedulingGateway.cancelBreakWarning(session.id)
        app.sessionRepository.endSession(session)
        prefs.edit().clear().commit()
        cancelAlarm(app)
        if (plan.makesWayForNextSession) {
            ScheduleReceiver.notifyStartingSoon(app, profile)
        }
    }

    fun plan(profile: BlockedProfileEntity, startedAt: Instant, zone: ZoneId = ZoneId.systemDefault()): Plan {
        var limit = MAX_SESSION_MILLIS
        val schedule = profile.schedule?.takeIf { it.isActive && profile.blockingStrategyId == StrategyIds.SCHEDULE }
            ?: return Plan(limit, makesWayForNextSession = false)

        schedule.automaticEndDurationMillis?.let { limit = minOf(limit, it) }

        // Still running when the same schedule's next session comes round: it ends at that
        // session's 5-minute reminder, and the new one starts on time. Unless that next one is
        // skipped — then nothing ends here.
        val searchFrom = ZonedDateTime.ofInstant(startedAt.plusMillis(REMINDER_LEAD_MILLIS + 60_000 - 1), zone)
        val nextStart = schedule.nextTrigger(searchFrom)?.toInstant()
        if (nextStart != null && !profile.scheduleSkip.skips(nextStart, zone)) {
            val makeWayAt = nextStart.toEpochMilli() - REMINDER_LEAD_MILLIS - startedAt.toEpochMilli()
            if (makeWayAt < limit) return Plan(maxOf(0, makeWayAt), makesWayForNextSession = true)
        }
        return Plan(limit, makesWayForNextSession = false)
    }

    private fun armAlarm(context: Context, inMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setWhileIdleBestEffort(System.currentTimeMillis() + maxOf(1_000, inMillis), pendingIntent(context))
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ScheduleReceiver::class.java).setAction(ScheduleReceiver.ACTION_CHILD_SESSION_LIMIT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
