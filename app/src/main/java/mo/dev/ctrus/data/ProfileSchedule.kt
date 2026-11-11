package mo.dev.ctrus.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Port of `BlockedProfileSchedule` in Ctrus/Models/Schedule.swift, trimmed to what the
 * "Schedule + Ctrus NFC" mode actually uses: weekdays, a start time, and an optional duration.
 * There is no stored end time. iOS keeps a hidden 23:59 "technical" end only because
 * DeviceActivityCenter demands an interval, and reusing it as a real end is what made its clock
 * count down. Here the only real end is [durationInHours]: `null` (Indefinite) runs until the
 * Ctrus is scanned and the clock counts up; otherwise the session stops on its own that many
 * hours after it started and the clock counts down to that.
 *
 * `days` stores [DayOfWeek] ISO values (Mon=1 … Sun=7) rather than iOS's Sunday-first raw values,
 * since nothing is shared across platforms and java.time is what every calculation here uses.
 */
@Serializable
data class ProfileSchedule(
    val days: List<Int> = emptyList(),
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val durationInHours: Int? = null,
    // Alarm mode only (spec D2). Nullable so schedules saved before it still decode; read through
    // [alarmTime], [alarmSoundId] and [alarmSnoozeMinutes], which fill in the defaults.
    val alarmHour: Int? = null,
    val alarmMinute: Int? = null,
    val alarmSound: String? = null,
    val snoozeMinutes: Int? = null,
    /** How many times the alarm can be snoozed: 1–3, or [UNLIMITED_SNOOZES]. Read through [alarmSnoozeLimit]. */
    val snoozeLimit: Int? = null,
) {
    val isActive: Boolean get() = days.isNotEmpty()

    val automaticEndDurationMillis: Long? get() = durationInHours?.let { it * 3_600_000L }

    val daysOfWeek: Set<DayOfWeek> get() = days.map(DayOfWeek::of).toSet()

    /**
     * Next moment, strictly after [now], that falls on a selected day at the start time shifted
     * by [offsetMinutes] (negative for the "5 minutes before" reminder — the offset is applied
     * before matching the weekday, so a 00:02 start on Monday yields a Sunday 23:57 reminder,
     * same wraparound as iOS's `reminderComponents`). Occurrences [skip] covers are passed over;
     * the check is on the occurrence's own start, not the shifted moment.
     */
    fun nextTrigger(
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
        offsetMinutes: Long = 0,
        skip: ScheduleSkip = ScheduleSkip(),
    ): ZonedDateTime? {
        if (!isActive) return null
        val selected = daysOfWeek
        val startTime = LocalTime.of(startHour, startMinute)
        // A week and a day covers every selected day (today included, when its time has already
        // passed); the extra weeks are for looking past a skip, which can run up to 30 days ahead.
        return (0L..42L)
            .map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek in selected }
            .map { ZonedDateTime.of(LocalDateTime.of(it, startTime), now.zone) }
            .filterNot { skip.skips(it.toInstant(), now.zone) }
            .map { it.plusMinutes(offsetMinutes) }
            .filter { it.isAfter(now) }
            .minOrNull()
    }

    /** Starts of the occurrences that haven't begun yet, from [now] up to the end of [lastDay]. */
    fun upcomingOccurrenceStarts(now: ZonedDateTime, lastDay: LocalDate): List<ZonedDateTime> {
        if (!isActive) return emptyList()
        val selected = daysOfWeek
        val startTime = LocalTime.of(startHour, startMinute)
        return generateSequence(now.toLocalDate()) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .filter { it.dayOfWeek in selected }
            .map { ZonedDateTime.of(LocalDateTime.of(it, startTime), now.zone) }
            .filter { it.isAfter(now) }
            .toList()
    }

    /** The end time, which is when Alarm mode's alarm rings: 07:00 unless chosen. */
    val alarmTime: LocalTime get() = LocalTime.of(alarmHour ?: 7, alarmMinute ?: 0)

    /** Falls back to the default for a sound that's no longer offered. */
    val alarmSoundId: String
        get() = alarmSound?.takeIf { it == VIBRATE_ALARM_SOUND || it in ALARM_TONE_IDS } ?: DEFAULT_ALARM_SOUND

    val alarmSnoozeMinutes: Int get() = (snoozeMinutes ?: DEFAULT_SNOOZE_MINUTES).coerceIn(1, 15)

    val alarmSnoozeLimit: Int get() = snoozeLimit?.takeIf { it in AVAILABLE_SNOOZE_LIMITS } ?: DEFAULT_SNOOZE_LIMIT

    /** Whether the alarm can still be snoozed after [snoozesUsed] snoozes (iOS 6b1cb42). */
    fun allowsSnooze(snoozesUsed: Int): Boolean =
        alarmSnoozeLimit == UNLIMITED_SNOOZES || snoozesUsed < alarmSnoozeLimit

    val hasValidAlarmTime: Boolean get() = alarmTime != LocalTime.of(startHour, startMinute)

    /**
     * When the alarm rings for a session that started at [start]: the first end time after it,
     * so an end at or before the start is the next day. Matched on the wall clock, so a daylight
     * saving change that night doesn't move it (a 07:00 that doesn't exist that day moves on to
     * the next valid time, like iOS's `.nextTime`).
     */
    fun alarmDateAfter(start: ZonedDateTime): ZonedDateTime {
        val sameDay = ZonedDateTime.of(start.toLocalDate(), alarmTime, start.zone)
        return if (sameDay.isAfter(start)) sameDay else ZonedDateTime.of(start.toLocalDate().plusDays(1), alarmTime, start.zone)
    }

    /**
     * The occurrence under way (started today or yesterday, not skipped) whose alarm hasn't
     * finished ringing, give or take [graceMillis]. Port of iOS's `currentAlarmOccurrenceStart`.
     */
    fun currentAlarmOccurrenceStart(
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
        graceMillis: Long,
        skip: ScheduleSkip = ScheduleSkip(),
    ): ZonedDateTime? {
        if (!isActive) return null
        val startTime = LocalTime.of(startHour, startMinute)
        for (daysBack in 0L..1L) {
            val day = now.toLocalDate().minusDays(daysBack)
            if (day.dayOfWeek !in daysOfWeek) continue
            val start = ZonedDateTime.of(day, startTime, now.zone)
            if (start.isAfter(now)) continue
            if (skip.skips(start.toInstant(), now.zone)) continue
            if (!now.isBefore(alarmDateAfter(start).plusNanos(graceMillis * 1_000_000))) continue
            return start
        }
        return null
    }

    /** The days the alarm rings on, which move to the next day when the end time is at or before the start. */
    val alarmDays: Set<DayOfWeek>
        get() = if (alarmTime <= LocalTime.of(startHour, startMinute)) daysOfWeek.map { it.plus(1) }.toSet() else daysOfWeek

    /**
     * The ring days in [locale]'s short names, from [firstDayOfWeek]: runs of 3 or more as a
     * range ("Mon–Fri"), the rest separated by commas ("Mon, Wed, Sat"). Port of iOS's `alarmDaysText`.
     */
    fun alarmDaysText(locale: java.util.Locale, firstDayOfWeek: DayOfWeek): String {
        val ringDays = alarmDays
        val week = (0L..6L).map { firstDayOfWeek.plus(it) }
        val runs = mutableListOf<MutableList<DayOfWeek>>()
        week.forEachIndexed { index, day ->
            if (day !in ringDays) return@forEachIndexed
            val last = runs.lastOrNull()?.last()
            if (last != null && week.indexOf(last) + 1 == index) runs.last() += day else runs += mutableListOf(day)
        }
        fun name(day: DayOfWeek) = day.getDisplayName(java.time.format.TextStyle.SHORT, locale).removeSuffix(".")
        return runs.joinToString(", ") { run ->
            if (run.size >= 3) "${name(run.first())}–${name(run.last())}" else run.joinToString(", ") { name(it) }
        }
    }

    companion object {
        /** Spec D3.2, in menu order. Each is a file in res/raw. */
        val ALARM_TONE_IDS = listOf(
            "ctrus_morning", "ctrus_lumen", "ctrus_lagoon", "ctrus_daybreak", "ctrus_timber",
            "ctrus_serenade", "ctrus_arcade", "ctrus_groove", "ctrus_oldclock", "ctrus_birdsong",
        )
        const val DEFAULT_ALARM_SOUND = "ctrus_morning"
        const val VIBRATE_ALARM_SOUND = "vibrate"
        val AVAILABLE_SOUNDS = ALARM_TONE_IDS + VIBRATE_ALARM_SOUND

        val AVAILABLE_SNOOZE_MINUTES = (1..15).toList()
        const val DEFAULT_SNOOZE_MINUTES = 9
        const val UNLIMITED_SNOOZES = 0
        val AVAILABLE_SNOOZE_LIMITS = listOf(1, 2, 3, UNLIMITED_SNOOZES)
        const val DEFAULT_SNOOZE_LIMIT = 1

        /** Mirrors `BlockedProfileSchedule.availableDurationsInHours`. `null` (Indefinite) is offered separately. */
        val AVAILABLE_DURATIONS_IN_HOURS = listOf(1, 2, 3, 4)

        /** Sunday-first, matching iOS's `Weekday.allCases` order in the day picker and summaries. */
        val DISPLAY_ORDER: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY).plus(
            generateSequence(DayOfWeek.MONDAY) { it.plus(1) }.take(6),
        )
    }
}
