package mo.dev.ctrus.data

import mo.dev.ctrus.strategy.StrategyIds
import mo.dev.ctrus.ui.profile.ProfileDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Alarm mode's schedule rules (spec D2–D4, D8). */
class AlarmScheduleTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val sunToThu = listOf(7, 1, 2, 3, 4)
    private val night = ProfileSchedule(days = sunToThu, startHour = 23, startMinute = 0, alarmHour = 7, alarmMinute = 0)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test
    fun defaultsAndUnknownSound() {
        val schedule = ProfileSchedule(alarmSound = "system_default")
        assertEquals(LocalTime.of(7, 0), schedule.alarmTime)
        assertEquals("ctrus_morning", schedule.alarmSoundId)
        assertEquals(9, schedule.alarmSnoozeMinutes)
        assertEquals("vibrate", ProfileSchedule(alarmSound = "vibrate").alarmSoundId)
    }

    @Test
    fun endAtOrBeforeStartRingsTheNextDay() {
        // Sunday 2 Nov 2025, 23:00 → Monday 07:00.
        assertEquals(at(2025, 11, 3, 7), night.alarmDateAfter(at(2025, 11, 2, 23)))
        val sameDay = night.copy(startHour = 6)
        assertEquals(at(2025, 11, 2, 7), sameDay.alarmDateAfter(at(2025, 11, 2, 6)))
    }

    @Test
    fun alarmFollowsTheWallClockAcrossDaylightSaving() {
        // Lisbon falls back on 26 Oct 2025 at 02:00: still 07:00 on the clock.
        val alarm = night.alarmDateAfter(at(2025, 10, 25, 23))
        assertEquals(7, alarm.hour)
        assertEquals(26, alarm.dayOfMonth)
    }

    @Test
    fun startAndEndTheSameIsInvalid() {
        assertFalse(night.copy(alarmHour = 23).hasValidAlarmTime)
        assertTrue(night.hasValidAlarmTime)
    }

    @Test
    fun ringDaysMoveToTheNextDayAndReadAsARange() {
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), night.alarmDays)
        assertEquals("Mon–Fri", night.alarmDaysText(Locale.ENGLISH, DayOfWeek.MONDAY))
        val scattered = ProfileSchedule(days = listOf(1, 3, 6), startHour = 6, alarmHour = 7)
        assertEquals("Mon, Wed, Sat", scattered.alarmDaysText(Locale.ENGLISH, DayOfWeek.MONDAY))
    }

    @Test
    fun occurrenceUnderWayIsFoundUntilHalfAnHourAfterTheAlarm() {
        val grace = 30L * 60 * 1000
        // Started Sunday 23:00; Monday 07:20 is still within the alarm's grace…
        assertEquals(at(2025, 11, 2, 23), night.currentAlarmOccurrenceStart(at(2025, 11, 3, 7, 20), grace))
        // …07:31 isn't.
        assertNull(night.currentAlarmOccurrenceStart(at(2025, 11, 3, 7, 31), grace))
        // Saturday isn't a selected day.
        assertNull(night.currentAlarmOccurrenceStart(at(2025, 11, 1, 23, 30), grace))
        // A skipped occurrence doesn't count.
        val skip = ScheduleSkip(skipNextOccurrence = at(2025, 11, 2, 23).toInstant())
        assertNull(night.currentAlarmOccurrenceStart(at(2025, 11, 3, 1), grace, skip))
    }

    @Test
    fun oneTimeAlarmGetsTheNextStartDayAndNoSkipping() {
        val draft = ProfileDraft(strategyId = StrategyIds.ALARM, scheduleStartHour = 23, isOneTimeAlarm = true, allowSkip = true)
        // Saturday 1 Nov 2025 at 22:59:30: 23:00 is less than a minute away, so it's Sunday's.
        val saved = draft.forSaving(ZonedDateTime.of(2025, 11, 1, 22, 59, 30, 0, zone))
        assertEquals(setOf(DayOfWeek.SUNDAY), saved.scheduleDays)
        assertFalse(saved.allowSkip)
        assertEquals(setOf(DayOfWeek.SATURDAY), draft.forSaving(at(2025, 11, 1, 20)).scheduleDays)
        assertNull(saved.schedule!!.durationInHours)
    }

    @Test
    fun switchingToAlarmDropsTheDuration() {
        val draft = ProfileDraft(strategyId = StrategyIds.SCHEDULE, scheduleDurationHours = 2).withStrategy(StrategyIds.ALARM)
        assertNull(draft.scheduleDurationHours)
        assertTrue(draft.useSchedule)
        assertFalse(draft.copy(scheduleDays = emptySet()).hasValidSchedule)
        assertTrue(draft.copy(isOneTimeAlarm = true).hasValidSchedule)
    }
}
