package mo.dev.ctrus.data

import java.util.Calendar
import java.util.Date

data class WeeklySessionInterval(val startTime: Date, val endTime: Date)

data class WeeklySessionAggregation(
    val dailyDurations: DoubleArray,
    val dailySessionCounts: IntArray,
    val overlappingSessionCount: Int,
    val totalFocusTime: Double,
)

/** Port of Ctrus/Utils/WeeklySessionAggregator.swift — clips each session to each day's window, handling multi-day sessions correctly. */
object WeeklySessionAggregator {
    fun aggregate(sessions: List<WeeklySessionInterval>, weekStart: Date, calendar: Calendar = Calendar.getInstance()): WeeklySessionAggregation {
        val weekEnd = addDays(weekStart, 7, calendar)
        val overlapping = sessions.filter { it.startTime.before(weekEnd) && it.endTime.after(weekStart) }

        val dailyDurations = DoubleArray(7)
        val dailySessionCounts = IntArray(7)

        for (dayOffset in 0 until 7) {
            val currentDay = addDays(weekStart, dayOffset, calendar)
            val nextDay = addDays(currentDay, 1, calendar)

            for (session in overlapping) {
                val overlapStart = maxOf(session.startTime, currentDay)
                val overlapEnd = minOf(session.endTime, nextDay)
                if (!overlapStart.before(overlapEnd)) continue

                dailyDurations[dayOffset] += (overlapEnd.time - overlapStart.time) / 1000.0
                dailySessionCounts[dayOffset] += 1
            }
        }

        return WeeklySessionAggregation(
            dailyDurations = dailyDurations,
            dailySessionCounts = dailySessionCounts,
            overlappingSessionCount = overlapping.size,
            totalFocusTime = dailyDurations.sum(),
        )
    }

    /** Locale-aware start of week at midnight (matches `Calendar.current.dateInterval(of: .weekOfYear, for:)`). */
    fun startOfWeek(date: Date, calendar: Calendar = Calendar.getInstance()): Date {
        val cal = calendar.clone() as Calendar
        cal.time = date
        cal[Calendar.HOUR_OF_DAY] = 0; cal[Calendar.MINUTE] = 0; cal[Calendar.SECOND] = 0; cal[Calendar.MILLISECOND] = 0
        val firstDayOfWeek = cal.firstDayOfWeek
        while (cal.get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return cal.time
    }

    private fun addDays(date: Date, days: Int, calendar: Calendar): Date {
        val cal = calendar.clone() as Calendar
        cal.time = date
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.time
    }

    private fun maxOf(a: Date, b: Date): Date = if (a.after(b)) a else b
    private fun minOf(a: Date, b: Date): Date = if (a.before(b)) a else b
}
