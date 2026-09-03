package mo.dev.ctrus.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Port of Ctrus/Utils/DateFormatters.swift. */
object DateFormatters {
    /** "1h 2m 3s" / "2m 3s" / "3s". */
    fun formatDuration(durationSeconds: Double): String {
        val total = durationSeconds.toInt()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /** "00:00:42" clock format. */
    fun formatDurationClock(intervalSeconds: Double): String {
        val total = max(0, intervalSeconds.toInt())
        val hours = total / 3600
        val minutes = total / 60 % 60
        val seconds = total % 60
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }

    /** "45 min" / "1h" / "1h 30m". */
    fun formatMinutes(durationInMinutes: Int): String =
        if (durationInMinutes <= 60) {
            "$durationInMinutes min"
        } else {
            val hours = durationInMinutes / 60
            val minutes = durationInMinutes % 60
            if (minutes == 0) "${hours}h" else "${hours}h ${minutes}m"
        }

    /** "1h 30m" / "45m", 0 -> "0m". */
    fun formatDurationHoursMinutes(intervalSeconds: Double): String {
        if (intervalSeconds <= 0) return "0m"
        val total = intervalSeconds.toInt()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /** "1h" / "45m", 0 -> "0m". */
    fun formatDurationShort(intervalSeconds: Double): String {
        if (intervalSeconds <= 0) return "0m"
        val total = intervalSeconds.toInt()
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        return if (hours > 0) "${hours}h" else "${minutes}m"
    }

    /** "Today" / "Yesterday" / "Jan 5, 2026" — the two relative labels are localized by the caller. */
    fun formatSessionDate(date: Date, todayLabel: String, yesterdayLabel: String): String {
        val today = Calendar.getInstance().apply { time = Date() }
        val target = Calendar.getInstance().apply { time = date }
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(target, today) -> todayLabel
            isSameDay(target, yesterday) -> yesterdayLabel
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    fun formatDayNumber(date: Date): String = SimpleDateFormat("d", Locale.getDefault()).format(date)

    /** "Wednesday, Jan 7" — the selected-day header above an insights chart. */
    fun formatSelectedDayHeader(date: Date): String = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(date)

    /** "Jan 5 - 11" (same month) / "Jan 29 - Feb 4" (different months). */
    fun formatWeekRange(start: Date, end: Date): String {
        val startCal = Calendar.getInstance().apply { time = start }
        val endCal = Calendar.getInstance().apply { time = end }
        val sameMonth = startCal.get(Calendar.MONTH) == endCal.get(Calendar.MONTH) && startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)

        return if (sameMonth) {
            "${SimpleDateFormat("MMM", Locale.getDefault()).format(start)} ${startCal.get(Calendar.DAY_OF_MONTH)} - ${endCal.get(Calendar.DAY_OF_MONTH)}"
        } else {
            "${SimpleDateFormat("MMM d", Locale.getDefault()).format(start)} - ${SimpleDateFormat("MMM d", Locale.getDefault()).format(end)}"
        }
    }

    /** "Jan 5, 2026 at 3:45 PM" — full date+time, used in session details. */
    fun formatDate(date: Date): String = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(date)

    /** "Sep 29" — the recovery-unlock reset date, matching iOS's `.dateTime.month().day()`. */
    fun formatMonthDay(date: Date): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(date)

    /** "Jan - Feb" (same year) / "Dec 2025 - Jan 2026" (different years). */
    fun formatMonthRange(start: Date, end: Date): String {
        val startCal = Calendar.getInstance().apply { time = start }
        val endCal = Calendar.getInstance().apply { time = end }
        val sameYear = startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR)

        return if (sameYear) {
            "${SimpleDateFormat("MMM", Locale.getDefault()).format(start)} - ${SimpleDateFormat("MMM", Locale.getDefault()).format(end)}"
        } else {
            "${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(start)} - ${SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(end)}"
        }
    }
}
