package mo.dev.ctrus.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
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
) {
    val isActive: Boolean get() = days.isNotEmpty()

    val automaticEndDurationMillis: Long? get() = durationInHours?.let { it * 3_600_000L }

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
        /** Mirrors `BlockedProfileSchedule.availableDurationsInHours`. `null` (Indefinite) is offered separately. */
        val AVAILABLE_DURATIONS_IN_HOURS = listOf(1, 2, 3, 4)

        /** Sunday-first, matching iOS's `Weekday.allCases` order in the day picker and summaries. */
        val DISPLAY_ORDER: List<DayOfWeek> = listOf(DayOfWeek.SUNDAY).plus(
            generateSequence(DayOfWeek.MONDAY) { it.plus(1) }.take(6),
        )
    }
}
