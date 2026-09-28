package mo.dev.ctrus.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Port of `BlockedProfileSchedule` in Ctrus/Models/Schedule.swift, trimmed to what the
 * "Schedule + Ctrus NFC" mode actually uses: weekdays + a start time. There is deliberately no
 * end time — stopping always requires the Ctrus NFC. iOS only keeps a hidden 23:59 end because
 * DeviceActivityCenter demands an interval; AlarmManager has no such requirement.
 *
 * `days` stores [DayOfWeek] ISO values (Mon=1 … Sun=7) rather than iOS's Sunday-first raw values,
 * since nothing is shared across platforms and java.time is what every calculation here uses.
 */
@Serializable
data class ProfileSchedule(
    val days: List<Int> = emptyList(),
    val startHour: Int = 9,
    val startMinute: Int = 0,
) {
    val isActive: Boolean get() = days.isNotEmpty()

    val daysOfWeek: Set<DayOfWeek> get() = days.map(DayOfWeek::of).toSet()

    /**
     * Next moment, strictly after [now], that falls on a selected day at the start time shifted
     * by [offsetMinutes] (negative for the "5 minutes before" reminder — the offset is applied
     * before matching the weekday, so a 00:02 start on Monday yields a Sunday 23:57 reminder,
     * same wraparound as iOS's `reminderComponents`).
     */
    fun nextTrigger(now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()), offsetMinutes: Long = 0): ZonedDateTime? {
        if (!isActive) return null
        val selected = daysOfWeek
        val startTime = LocalTime.of(startHour, startMinute)
        // 8 candidate days (today through a week from today) covers the case where today is the
        // only selected day and its time has already passed.
        return (0L..8L)
            .map { now.toLocalDate().plusDays(it) }
            .filter { it.dayOfWeek in selected }
            .map { ZonedDateTime.of(LocalDateTime.of(it, startTime), now.zone).plusMinutes(offsetMinutes) }
            .filter { it.isAfter(now) }
            .minOrNull()
    }

    companion object {
        /** Sunday-first, matching iOS's `Weekday.allCases` order in the day picker and summaries. */
        val DISPLAY_ORDER: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY).plus(
            generateSequence(DayOfWeek.MONDAY) { it.plus(1) }.take(6),
        )
    }
}
