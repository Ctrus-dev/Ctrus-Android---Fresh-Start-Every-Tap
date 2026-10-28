package mo.dev.ctrus.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Port of iOS's `ScheduleSkip`: occurrences of a schedule the person chose to skip, without
 * touching the schedule itself.
 *
 * @property skipNextOccurrence start of the one occurrence to skip.
 * @property skipUntil start of the last skipped day; every occurrence up to and including it is skipped.
 */
data class ScheduleSkip(
    val skipNextOccurrence: Instant? = null,
    val skipUntil: Instant? = null,
) {
    fun skips(occurrenceStart: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val day = occurrenceStart.atZone(zone).toLocalDate()
        if (skipNextOccurrence != null && skipNextOccurrence.atZone(zone).toLocalDate() == day) return true
        if (skipUntil != null && !day.isAfter(skipUntil.atZone(zone).toLocalDate())) return true
        return false
    }

    // The single skip is kept until that whole day is over rather than just past its start time —
    // the start alarm can fire a little late, and must still find it there when it does.
    fun isNextOccurrenceSkipActive(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        skipNextOccurrence != null && now.atZone(zone).toLocalDate() <= skipNextOccurrence.atZone(zone).toLocalDate()

    fun isSkipUntilActive(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        skipUntil != null && now.atZone(zone).toLocalDate() <= skipUntil.atZone(zone).toLocalDate()

    fun isActive(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        isNextOccurrenceSkipActive(now, zone) || isSkipUntilActive(now, zone)

    fun clearingExpired(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): ScheduleSkip = ScheduleSkip(
        skipNextOccurrence = skipNextOccurrence.takeIf { isNextOccurrenceSkipActive(now, zone) },
        skipUntil = skipUntil.takeIf { isSkipUntilActive(now, zone) },
    )

    companion object {
        /** "Skip until…" offers today and the next 30 days. */
        const val MAX_DAYS_AHEAD = 30L

        fun startOfDay(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Instant = day.atStartOfDay(zone).toInstant()
    }
}
