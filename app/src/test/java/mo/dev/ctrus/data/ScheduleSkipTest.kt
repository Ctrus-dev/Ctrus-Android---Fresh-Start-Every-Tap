package mo.dev.ctrus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Port of iOS's ScheduleSkipTests. */
class ScheduleSkipTest {
    private val zone = ZoneId.of("Europe/Lisbon")

    // Monday, 2 Nov 2026
    private fun date(day: Int, hour: Int = 12, minute: Int = 0, month: Int = 11) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone)

    private val schedule = ProfileSchedule(
        days = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).map { it.value },
        startHour = 22,
        startMinute = 50,
        durationInHours = 2,
    )

    @Test
    fun skipNextSkipsThatOccurrenceButNotTheNext() {
        val skip = ScheduleSkip(skipNextOccurrence = date(2, 22, 50).toInstant())
        assertTrue(skip.skips(date(2, 22, 51).toInstant(), zone))
        assertFalse(skip.skips(date(4, 22, 50).toInstant(), zone))
    }

    @Test
    fun skipUntilSkipsThroughThatDayInclusive() {
        val skip = ScheduleSkip(skipUntil = date(6, 0, 0).toInstant())
        assertTrue(skip.skips(date(6, 22, 50).toInstant(), zone))
        assertFalse(skip.skips(date(9, 22, 50).toInstant(), zone))
    }

    @Test
    fun skipUntilExpiresTheDayAfter() {
        val skip = ScheduleSkip(skipUntil = date(6, 0, 0).toInstant())
        assertTrue(skip.isActive(date(6, 23, 59).toInstant(), zone))
        assertFalse(skip.isActive(date(7, 0, 0).toInstant(), zone))
        assertNull(skip.clearingExpired(date(7, 0, 1).toInstant(), zone).skipUntil)
    }

    @Test
    fun skipNextExpiresWhenItsDayEnds() {
        val skip = ScheduleSkip(skipNextOccurrence = date(2, 22, 50).toInstant())
        assertTrue(skip.isActive(date(2, 23, 30).toInstant(), zone))
        assertFalse(skip.isActive(date(3, 0, 1).toInstant(), zone))
        assertNull(skip.clearingExpired(date(3, 0, 1).toInstant(), zone).skipNextOccurrence)
    }

    @Test
    fun countingUntilAFridayLeavesOutTodaysStartedSession() {
        val starts = schedule.upcomingOccurrenceStarts(date(2, 23, 0), LocalDate.of(2026, 11, 6))
        // Wednesday and Friday — Monday's already began.
        assertEquals(listOf(date(4, 22, 50), date(6, 22, 50)), starts)
    }

    @Test
    fun countingUntilATuesdayWithOnlyTodaysStartedSessionCountsNone() {
        assertTrue(schedule.upcomingOccurrenceStarts(date(2, 23, 0), LocalDate.of(2026, 11, 3)).isEmpty())
    }

    @Test
    fun reminderOfASkippedOccurrenceGoesToTheNextOne() {
        val skip = ScheduleSkip(skipNextOccurrence = date(2, 22, 50).toInstant())
        val reminder = schedule.nextTrigger(date(2, 22, 0), offsetMinutes = -5, skip = skip)
        assertEquals(date(4, 22, 45), reminder)
    }

    @Test
    fun reminderOfAnUnskippedDayIsToday() {
        assertEquals(date(2, 22, 45), schedule.nextTrigger(date(2, 22, 0), offsetMinutes = -5))
    }

    @Test
    fun nextStartPassesOverSkippedOccurrences() {
        val skip = ScheduleSkip(
            skipNextOccurrence = date(2, 22, 50).toInstant(),
            skipUntil = date(6, 0, 0).toInstant(),
        )
        assertEquals(date(9, 22, 50), schedule.nextTrigger(date(2, 9), skip = skip))
    }

    @Test
    fun nextStartLooksPastA30DaySkip() {
        val skip = ScheduleSkip(skipUntil = date(30, 0, 0).toInstant())
        // Monday 30 Nov is still skipped; Wednesday 2 Dec is the first back.
        assertEquals(date(2, 22, 50, month = 12), schedule.nextTrigger(date(2, 9), skip = skip))
    }

    @Test
    fun skipIsIgnoredWhenNotAllowed() {
        val profile = BlockedProfileEntity(
            name = "Test",
            blockingStrategyId = "schedule",
            allowSkip = false,
            skipUntil = date(6, 0, 0).toInstant().toEpochMilli(),
        )
        assertEquals(ScheduleSkip(), profile.scheduleSkip)
    }
}
