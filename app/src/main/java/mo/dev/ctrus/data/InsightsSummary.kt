package mo.dev.ctrus.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WeeklyDayAggregate(val dayName: String, val displayLabel: String, val totalSessionSeconds: Double, val sessionCount: Int, val date: Date)

data class WeeklySummary(
    val days: List<WeeklyDayAggregate>,
    val totalSessions: Int,
    val averageSessionDuration: Double,
    val totalFocusTime: Double,
    val weekStartDate: Date,
    val weekEndDate: Date,
)

data class MonthlyDayAggregate(val dayOfMonth: Int, val dayName: String, val totalSessionSeconds: Double, val sessionCount: Int, val date: Date)

data class MonthlySummary(
    val days: List<MonthlyDayAggregate>,
    val totalSessions: Int,
    val averageSessionDuration: Double,
    val totalFocusTime: Double,
    val monthStartDate: Date,
    val monthEndDate: Date,
)

data class ProfileInsightsMetrics(val totalCompletedSessions: Int, val totalFocusTime: Double, val totalBreakTime: Double)

/**
 * Port of the summary-computation halves of WeeklyInsightsUtil.swift/MonthlyInsightsUtil.swift/
 * ProfileInsightsUtil.swift — kept as pure functions (no ObservableObject/ViewModel wrapper)
 * since [mo.dev.ctrus.ui.insights.ProfileInsightsScreen] just calls these from `remember`.
 */
object InsightsSummary {
    private val dayNameFormat = SimpleDateFormat("EEE", Locale.getDefault())

    fun weeklySummary(sessions: List<BlockedProfileSessionEntity>, selectedDate: Date, calendar: Calendar = Calendar.getInstance()): WeeklySummary {
        val weekStart = WeeklySessionAggregator.startOfWeek(selectedDate, calendar)
        val weekEnd = addDays(weekStart, 6, calendar)

        val intervals = sessions.mapNotNull { session ->
            val end = session.endTimeEpochMilli ?: return@mapNotNull null
            WeeklySessionInterval(Date(session.startTimeEpochMilli), Date(end))
        }
        val aggregation = WeeklySessionAggregator.aggregate(intervals, weekStart, calendar)

        val days = (0 until 7).map { dayOffset ->
            val currentDay = addDays(weekStart, dayOffset, calendar)
            val cal = (calendar.clone() as Calendar).apply { time = currentDay }
            val dayNumber = cal.get(Calendar.DAY_OF_MONTH)
            val dayName = dayNameFormat.format(currentDay)
            WeeklyDayAggregate(
                dayName = dayName,
                displayLabel = "$dayName $dayNumber",
                totalSessionSeconds = aggregation.dailyDurations[dayOffset],
                sessionCount = aggregation.dailySessionCounts[dayOffset],
                date = currentDay,
            )
        }

        val totalSessions = aggregation.overlappingSessionCount
        val totalFocusTime = aggregation.totalFocusTime
        return WeeklySummary(
            days = days,
            totalSessions = totalSessions,
            averageSessionDuration = if (totalSessions > 0) totalFocusTime / totalSessions else 0.0,
            totalFocusTime = totalFocusTime,
            weekStartDate = weekStart,
            weekEndDate = weekEnd,
        )
    }

    fun monthlySummary(sessions: List<BlockedProfileSessionEntity>, selectedDate: Date, calendar: Calendar = Calendar.getInstance()): MonthlySummary {
        val monthStart = MonthlySessionAggregator.startOfMonth(selectedDate, calendar)
        val daysInMonth = MonthlySessionAggregator.daysInMonth(selectedDate, calendar)
        val monthEnd = addDays(monthStart, daysInMonth - 1, calendar)

        val intervals = sessions.mapNotNull { session ->
            val end = session.endTimeEpochMilli ?: return@mapNotNull null
            MonthlySessionInterval(Date(session.startTimeEpochMilli), Date(end))
        }
        val aggregation = MonthlySessionAggregator.aggregate(intervals, monthStart, calendar)

        val days = (0 until daysInMonth).map { dayOffset ->
            val currentDay = addDays(monthStart, dayOffset, calendar)
            val cal = (calendar.clone() as Calendar).apply { time = currentDay }
            MonthlyDayAggregate(
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                dayName = dayNameFormat.format(currentDay),
                totalSessionSeconds = aggregation.dailyDurations[dayOffset],
                sessionCount = aggregation.dailySessionCounts[dayOffset],
                date = currentDay,
            )
        }

        val totalSessions = aggregation.overlappingSessionCount
        val totalFocusTime = aggregation.totalFocusTime
        return MonthlySummary(
            days = days,
            totalSessions = totalSessions,
            averageSessionDuration = if (totalSessions > 0) totalFocusTime / totalSessions else 0.0,
            totalFocusTime = totalFocusTime,
            monthStartDate = monthStart,
            monthEndDate = monthEnd,
        )
    }

    /** Only totalFocusTime/totalBreakTime are surfaced in the UI — the rest of ProfileInsightsUtil's metrics have no call site. */
    fun metrics(sessions: List<BlockedProfileSessionEntity>, profile: BlockedProfileEntity): ProfileInsightsMetrics {
        val completed = sessions.filter { it.endTimeEpochMilli != null }
        val totalFocus = completed.sumOf { it.duration() / 1000.0 }
        val totalBreak = completed.sumOf { it.usedBreakDurationIncludingActive(profile) }
        return ProfileInsightsMetrics(completed.size, totalFocus, totalBreak)
    }

    private fun addDays(date: Date, days: Int, calendar: Calendar): Date {
        val cal = (calendar.clone() as Calendar).apply { time = date }
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.time
    }
}
