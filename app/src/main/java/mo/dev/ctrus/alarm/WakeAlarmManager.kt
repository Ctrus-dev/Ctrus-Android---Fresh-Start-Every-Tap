package mo.dev.ctrus.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.MainActivity
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.permissions.AccessibilityPermissionUtil
import mo.dev.ctrus.scheduling.ScheduleReceiver
import mo.dev.ctrus.strategy.StrategyIds
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Alarm mode's alarm, from the night before to the Ctrus tap — the Android side of iOS's
 * WakeAlarmManager + AlarmScheduleUtil (spec D4–D8).
 *
 * The alarm for a profile's next session is set ahead of time with [AlarmManager.setAlarmClock]:
 * the main alarm plus 30 spares a minute apart, so it comes back if it's silenced some other way
 * than the Ctrus (the app force-stopped, the notification dismissed). Each one wakes
 * [AlarmReceiver], which starts [AlarmService] while the alarm is due. The service plays the sound
 * whether the app is open or not, until the session ends or the alarm is snoozed.
 *
 * Every change goes through [refresh]/[refreshAll], one at a time, which work out when the alarm
 * should ring and re-set it if what's armed doesn't match.
 */
object WakeAlarmManager {
    private const val SPARE_COUNT = 31
    private const val SPARE_INTERVAL_MILLIS = 60_000L

    /** How long after its alarm an occurrence that never got a session is still started by the app (D8). */
    const val MISSED_SESSION_GRACE_MILLIS = 30L * 60 * 1000

    /** While it rings, the spares are kept this far ahead, so one comes back soon if the sound is killed. */
    const val RINGING_SPARES_LEAD_MILLIS = 60_000L

    /** A session counts for an occurrence when it started at most this long before it. */
    private const val OCCURRENCE_MATCH_SLACK_MILLIS = 60_000L

    @Serializable
    data class AlarmRecord(
        /** When this alarm is due, kept while the spares move about for a snooze or while it rings. */
        val alarmDate: Long,
        /** Where the main alarm and its spares currently start. */
        val sparesAt: Long,
        val requestCodes: List<Int>,
        val allowsSnooze: Boolean,
        val soundId: String,
        val profileName: String,
    )

    private const val KEY_RECORDS = "records"
    private const val KEY_SNOOZED_UNTIL = "snoozedUntil"
    private const val KEY_SESSION_ENDS = "sessionEnds"

    private val json = Json { ignoreUnknownKeys = true }
    private val recordsSerializer = MapSerializer(String.serializer(), AlarmRecord.serializer())
    private val sessionEndsSerializer = MapSerializer(String.serializer(), Long.serializer())
    private lateinit var prefs: SharedPreferences

    private val _records = MutableStateFlow<Map<String, AlarmRecord>>(emptyMap())
    val records: StateFlow<Map<String, AlarmRecord>> = _records

    private val _snoozedUntil = MutableStateFlow<Long?>(null)
    val snoozedUntil: StateFlow<Long?> = _snoozedUntil

    /** Set when the alarm opens the app, so that profile's card opens expanded (D6.1). */
    val expandRequestProfileId = MutableStateFlow<String?>(null)
    /** Set alongside it, so that card goes straight to reading the Ctrus. */
    val stopRequestProfileId = MutableStateFlow<String?>(null)

    // Refreshes run one after another, so two at once can't both arm alarms for the same profile
    // and leave one set of them behind.
    private val mutex = Mutex()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mo.dev.ctrus.wake_alarm", Context.MODE_PRIVATE)
        _records.value = prefs.getString(KEY_RECORDS, null)
            ?.let { runCatching { json.decodeFromString(recordsSerializer, it) }.getOrNull() }.orEmpty()
        _snoozedUntil.value = prefs.getLong(KEY_SNOOZED_UNTIL, 0).takeIf { it > 0 }
    }

    fun isAlarmProfile(profile: BlockedProfileEntity?): Boolean = profile?.blockingStrategyId == StrategyIds.ALARM

    // region Keeping the alarms in line with the profiles

    /** Sets, moves or cancels this profile's alarm to match its schedule and session. Safe to call any time. */
    suspend fun refresh(app: CtrusApp, profileId: String) = mutex.withLock { refreshLocked(app, profileId, rearm = false) }

    /**
     * Every profile, plus records left by deleted ones. With [rearm] (after a reboot, an update or
     * a clock change, when the system has dropped or may misplace the alarms), alarms that already
     * match are set again too.
     */
    suspend fun refreshAll(app: CtrusApp, rearm: Boolean = false) = mutex.withLock {
        deleteFinishedOneTimeProfiles(app)
        val ids = app.profileRepository.getAll().map { it.id }.toSet() + _records.value.keys
        ids.forEach { refreshLocked(app, it, rearm) }
    }

    private suspend fun refreshLocked(app: CtrusApp, profileId: String, rearm: Boolean) {
        val record = _records.value[profileId]
        val profile = app.profileRepository.find(profileId)
        val desiredDate = profile?.let { desiredAlarmDate(app, it) }

        if (profile == null || desiredDate == null) {
            if (record != null) {
                cancel(app, record)
                setRecord(profileId, null)
            }
            return
        }

        val allowsSnooze = profile.enableSnooze
        val soundId = profile.schedule?.alarmSoundId ?: mo.dev.ctrus.data.ProfileSchedule.DEFAULT_ALARM_SOUND
        if (record != null && record.alarmDate == desiredDate && record.allowsSnooze == allowsSnooze &&
            record.soundId == soundId && record.profileName == profile.name
        ) {
            // Already set for this alarm; a due one is looked after by the ringing and the snooze.
            if (rearm || record.requestCodes.size < SPARE_COUNT) {
                val at = maxOf(record.sparesAt, System.currentTimeMillis() + 1_000)
                setRecord(profileId, record.copy(sparesAt = at, requestCodes = arm(app, profileId, at)))
            }
            return
        }

        record?.let { cancel(app, it) }
        // A running session's alarm can already be due.
        val at = maxOf(desiredDate, System.currentTimeMillis() + 1_000)
        setRecord(
            profileId,
            AlarmRecord(
                alarmDate = desiredDate,
                sparesAt = at,
                requestCodes = arm(app, profileId, at),
                allowsSnooze = allowsSnooze,
                soundId = soundId,
                profileName = profile.name,
            ),
        )
    }

    /** The running session's alarm, or else the next session's (spec D4). */
    private suspend fun desiredAlarmDate(app: CtrusApp, profile: BlockedProfileEntity): Long? {
        if (!isAlarmProfile(profile)) return null
        val schedule = profile.schedule?.takeIf { it.isActive } ?: return null
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)

        val active = app.sessionRepository.mostRecentActive()
        if (active != null && active.profileId == profile.id) {
            return schedule.alarmDateAfter(Instant.ofEpochMilli(active.startTimeEpochMilli).atZone(zone)).toInstant().toEpochMilli()
        }

        // An occurrence under way without a session gets one from the app (D8), so its alarm
        // stays — unless its session already ran and was stopped. With another profile's session
        // running, that occurrence was skipped (ScheduleReceiver never replaces a session), so it
        // has no alarm either.
        if (active == null) {
            val start = schedule.currentAlarmOccurrenceStart(now, MISSED_SESSION_GRACE_MILLIS, profile.scheduleSkip)
            if (start != null && !hasSessionSince(app, profile.id, start)) {
                return schedule.alarmDateAfter(start).toInstant().toEpochMilli()
            }
        }

        // Otherwise the next occurrence's.
        val next = schedule.nextTrigger(now, skip = profile.scheduleSkip) ?: return null
        return schedule.alarmDateAfter(next).toInstant().toEpochMilli()
    }

    private suspend fun hasSessionSince(app: CtrusApp, profileId: String, occurrenceStart: ZonedDateTime): Boolean {
        val since = occurrenceStart.toInstant().toEpochMilli() - OCCURRENCE_MATCH_SLACK_MILLIS
        if ((sessionEnds()[profileId] ?: 0) > since) return true
        return app.sessionRepository.forProfile(profileId).any { it.startTimeEpochMilli >= since }
    }

    /**
     * "Ring only once" profiles whose one session is over (D3.4). Normally deleted as the session
     * ends ([onSessionEnded]); this catches one the app was killed before deleting.
     */
    private suspend fun deleteFinishedOneTimeProfiles(app: CtrusApp) {
        val active = app.sessionRepository.mostRecentActive()
        app.profileRepository.getAll()
            .filter { isAlarmProfile(it) && it.isOneTimeAlarm && it.id != active?.profileId }
            .filter { profile -> app.sessionRepository.forProfile(profile.id).any { it.endTimeEpochMilli != null } }
            .forEach { app.profileRepository.delete(it) }
    }

    // endregion

    // region Session start and end

    /**
     * The session ended — Ctrus, emergency unblock, the parents, or Child Mode's limit: silence it,
     * forget the snooze, set the next occurrence's alarm, and delete a "Ring only once" profile.
     */
    suspend fun onSessionEnded(app: CtrusApp, profileId: String) {
        val profile = app.profileRepository.find(profileId) ?: return
        if (!isAlarmProfile(profile)) return
        mutex.withLock {
            setSnoozedUntil(null)
            setSessionEnd(profileId, System.currentTimeMillis())
            _records.value[profileId]?.let {
                cancel(app, it)
                setRecord(profileId, null)
            }
            refreshLocked(app, profileId, rearm = false)
        }
        AlarmService.stop(app)
        if (profile.isOneTimeAlarm) {
            // Whatever is still showing the profile (the Ctrus reading, the emergency sheet) closes first.
            kotlinx.coroutines.delay(500)
            app.profileRepository.find(profileId)?.let { app.profileRepository.delete(it) }
        }
    }

    // endregion

    // region Ringing

    /**
     * The profile whose alarm should be ringing right now, if any: its alarm time has passed, it
     * isn't snoozed, and its session is the one running (or none is running yet — D8 then starts
     * it when the app is opened).
     */
    suspend fun ringingProfileId(app: CtrusApp, now: Long = System.currentTimeMillis()): String? {
        if ((_snoozedUntil.value ?: 0) > now) return null
        val due = _records.value.filterValues { it.alarmDate <= now }.keys
        if (due.isEmpty()) return null
        val active = app.sessionRepository.mostRecentActive()
        return if (active != null) active.profileId.takeIf { it in due } else due.first()
    }

    /** Starts the sound if the alarm is due and isn't already ringing — e.g. when the app is opened after the spares ran out. */
    suspend fun ensureRinging(app: CtrusApp) {
        if (!AlarmService.isRunning && ringingProfileId(app) != null) AlarmService.start(app)
    }

    /** While it rings, the spares are kept a minute ahead, so the alarm comes back soon if the sound is killed. */
    suspend fun keepSparesAhead(app: CtrusApp, profileId: String) = mutex.withLock {
        moveSparesLocked(app, profileId, System.currentTimeMillis() + RINGING_SPARES_LEAD_MILLIS)
    }

    /** D6.3: silences it for the profile's snooze time, then it rings again. */
    suspend fun snooze(app: CtrusApp, profileId: String) {
        val profile = app.profileRepository.find(profileId) ?: return
        if (!profile.enableSnooze) return
        val minutes = profile.schedule?.alarmSnoozeMinutes ?: mo.dev.ctrus.data.ProfileSchedule.DEFAULT_SNOOZE_MINUTES
        val until = System.currentTimeMillis() + minutes * 60_000L
        mutex.withLock {
            setSnoozedUntil(until)
            moveSparesLocked(app, profileId, until)
        }
        AlarmService.stop(app)
    }

    /**
     * Slid away on the alarm screen: the app opens on this profile (D6.1), and the sound carries
     * on. A session the system never started is started now (D8); if there still isn't one,
     * there's nothing to stop, so this occurrence's alarm is let go.
     */
    suspend fun alarmOpenedApp(app: CtrusApp, profileId: String) {
        expandRequestProfileId.value = profileId
        stopRequestProfileId.value = profileId
        startMissedAlarmSession(app)
        if (app.sessionRepository.mostRecentActive()?.profileId != profileId) {
            mutex.withLock {
                setSessionEnd(profileId, System.currentTimeMillis())
                refreshLocked(app, profileId, rearm = false)
            }
            if (ringingProfileId(app) == null) AlarmService.stop(app)
        }
    }

    /**
     * D8: the start alarm can fire late or not at all (app force-stopped, the system holding it
     * back). The alarm is set regardless, so without a session it would ring and then stop as soon
     * as the app was opened, Ctrus or not. Started from the occurrence's own start, so the alarm
     * stays where it was. One profile at a time.
     */
    suspend fun startMissedAlarmSession(app: CtrusApp) = ScheduleReceiver.startLock.withLock {
        if (app.sessionRepository.mostRecentActive() != null) return@withLock
        if (!AccessibilityPermissionUtil.isEnabled(app)) return@withLock
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        for (profile in app.profileRepository.getAll().filter(::isAlarmProfile)) {
            val schedule = profile.schedule ?: continue
            val start = schedule.currentAlarmOccurrenceStart(now, MISSED_SESSION_GRACE_MILLIS, profile.scheduleSkip) ?: continue
            if (hasSessionSince(app, profile.id, start)) continue
            app.sessionRepository.create(
                profile.id,
                tag = StrategyIds.ALARM,
                forceStarted = true,
                startTimeEpochMilli = start.toInstant().toEpochMilli(),
            )
            refresh(app, profile.id)
            return@withLock
        }
    }

    // endregion

    // region AlarmManager

    private suspend fun moveSparesLocked(app: CtrusApp, profileId: String, at: Long) {
        val record = _records.value[profileId] ?: return
        val profile = app.profileRepository.find(profileId) ?: return
        cancel(app, record)
        setRecord(
            profileId,
            record.copy(
                sparesAt = at,
                requestCodes = arm(app, profileId, at),
                allowsSnooze = profile.enableSnooze,
                soundId = profile.schedule?.alarmSoundId ?: record.soundId,
                profileName = profile.name,
            ),
        )
    }

    /** The main alarm and its spares from [at]. If the system refuses one, keeps the ones it got. */
    private fun arm(context: Context, profileId: String, at: Long): List<Int> {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode(profileId, -1),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val codes = mutableListOf<Int>()
        for (index in 0 until SPARE_COUNT) {
            val code = requestCode(profileId, index)
            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(at + index * SPARE_INTERVAL_MILLIS, showIntent),
                    ringIntent(context, profileId, code, PendingIntent.FLAG_UPDATE_CURRENT)!!,
                )
                codes += code
            } catch (e: SecurityException) {
                // "Alarms & reminders" was turned off. Re-armed once it's back
                // (SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED → refreshAll(rearm = true)).
                android.util.Log.w("CtrusAlarm", "Stopped setting alarms at ${codes.size}", e)
                break
            }
        }
        return codes
    }

    private fun cancel(context: Context, record: AlarmRecord) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        // The codes don't depend on the record, so a partly armed set is cancelled in full too.
        record.requestCodes.forEach { code ->
            ringIntent(context, profileIdForCancel = null, code = code, flags = PendingIntent.FLAG_NO_CREATE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
    }

    private fun ringIntent(context: Context, profileIdForCancel: String?, code: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmReceiver.ACTION_RING
                profileIdForCancel?.let { putExtra(AlarmReceiver.EXTRA_PROFILE_ID, it) }
            },
            flags or PendingIntent.FLAG_IMMUTABLE,
        )

    // Intent.filterEquals() ignores extras, so each profile and spare needs its own request code.
    private fun requestCode(profileId: String, index: Int): Int = "alarm:$profileId:$index".hashCode()

    // endregion

    // region Storage

    private fun setRecord(profileId: String, record: AlarmRecord?) {
        val all = _records.value.toMutableMap()
        if (record == null) all.remove(profileId) else all[profileId] = record
        prefs.edit().putString(KEY_RECORDS, json.encodeToString(recordsSerializer, all)).commit()
        _records.value = all
    }

    private fun setSnoozedUntil(until: Long?) {
        prefs.edit().putLong(KEY_SNOOZED_UNTIL, until ?: 0).commit()
        _snoozedUntil.value = until
    }

    private fun sessionEnds(): Map<String, Long> =
        prefs.getString(KEY_SESSION_ENDS, null)
            ?.let { runCatching { json.decodeFromString(sessionEndsSerializer, it) }.getOrNull() }.orEmpty()

    private fun setSessionEnd(profileId: String, at: Long) {
        val all = sessionEnds() + (profileId to at)
        prefs.edit().putString(KEY_SESSION_ENDS, json.encodeToString(sessionEndsSerializer, all)).commit()
    }

    // endregion
}
