package mo.dev.ctrus.data

import java.util.Calendar
import java.util.Date

data class MonthlySessionInterval(val startTime: Date, val endTime: Date)

data class MonthlySessionAggregation(
    val dailyDurations: DoubleArray,
    val dailySessionCounts: IntArray,
    val overlappingSessionCount: Int,
    val totalFocusTime: Double,
    val daysInMonth: Int,
)

/** Port of Ctrus/Utils/MonthlySessionAggregator.swift. */
object MonthlySessionAggregator {
    fun aggregate(sessions: List<MonthlySessionInterval>, monthStart: Date, calendar: Calendar = Calendar.getInstance()): MonthlySessionAggregation {
        val daysInMonth = daysInMonth(monthStart, calendar)
        val monthEnd = addDays(monthStart, daysInMonth, calendar)

        val overlapping = sessions.filter { it.startTime.before(monthEnd) && it.endTime.after(monthStart) }

        val dailyDurations = DoubleArray(daysInMonth)
        val dailySessionCounts = IntArray(daysInMonth)

        for (dayOffset in 0 until daysInMonth) {
            val currentDay = addDays(monthStart, dayOffset, calendar)
            val nextDay = addDays(currentDay, 1, calendar)

            for (session in overlapping) {
                val overlapStart = maxOf(session.startTime, currentDay)
                val overlapEnd = minOf(session.endTime, nextDay)
                if (!overlapStart.before(overlapEnd)) continue

                dailyDurations[dayOffset] += (overlapEnd.time - overlapStart.time) / 1000.0
                dailySessionCounts[dayOffset] += 1
            }
        }

        return MonthlySessionAggregation(
            dailyDurations = dailyDurations,
            dailySessionCounts = dailySessionCounts,
            overlappingSessionCount = overlapping.size,
            totalFocusTime = dailyDurations.sum(),
            daysInMonth = daysInMonth,
        )
    }

    fun startOfMonth(date: Date, calendar: Calendar = Calendar.getInstance()): Date {
        val cal = calendar.clone() as Calendar
        cal.time = date
        cal[Calendar.DAY_OF_MONTH] = 1
        cal[Calendar.HOUR_OF_DAY] = 0; cal[Calendar.MINUTE] = 0; cal[Calendar.SECOND] = 0; cal[Calendar.MILLISECOND] = 0
        return cal.time
    }

    fun daysInMonth(date: Date, calendar: Calendar = Calendar.getInstance()): Int {
        val cal = calendar.clone() as Calendar
        cal.time = date
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
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
