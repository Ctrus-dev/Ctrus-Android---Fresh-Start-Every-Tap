package mo.dev.ctrus.data

import mo.dev.ctrus.strategy.StrategyCapabilities
import java.util.Date

/**
 * Port of Ctrus/Utils/SessionTimeCalculator.swift, trimmed to what the two reachable strategies
 * (Ctrus NFC, Manual + Ctrus NFC) actually need: neither has an overall duration limit or a
 * schedule, so the only thing that produces a countdown-to-a-fixed-end-time instead of a
 * plain elapsed-time display is an active break.
 */
object SessionTimeCalculator {
    /** Wall-clock elapsed time minus time spent on break — what "focus time" actually counts. */
    fun elapsedFocusTimeSeconds(session: BlockedProfileSessionEntity, profile: BlockedProfileEntity, at: Date = Date()): Double {
        val rawElapsed = (at.time - session.startTimeEpochMilli).toDouble() / 1000.0
        val breakDuration = calculateBreakDuration(session, profile, at)
        return maxOf(0.0, rawElapsed - breakDuration)
    }

    /** What the UI should show: a countdown to [expectedEndTime] if one applies, else elapsed focus time. */
    fun displayedTimeSeconds(
        session: BlockedProfileSessionEntity,
        profile: BlockedProfileEntity,
        elapsedFocusTimeSeconds: Double? = null,
        at: Date = Date(),
    ): Double {
        val expectedEnd = expectedEndTime(session, profile)
        if (expectedEnd != null) {
            return maxOf(0.0, (expectedEnd.time - at.time).toDouble() / 1000.0)
        }
        return elapsedFocusTimeSeconds ?: elapsedFocusTimeSeconds(session, profile, at)
    }

    fun expectedEndTime(session: BlockedProfileSessionEntity, profile: BlockedProfileEntity): Date? {
        val breakStart = session.breakStartTimeEpochMilli
        val allowsBreaks = StrategyCapabilities.allowsTimedBreaks(profile.blockingStrategyId)
        if (breakStart != null && session.isBreakActive(profile, allowsBreaks)) {
            val remaining = if (profile.allowMultipleBreaks) {
                session.remainingBreakAllowance(profile, breakStart)
            } else {
                session.totalBreakAllowanceSeconds(profile)
            }
            return Date(breakStart + (remaining * 1000).toLong())
        }
        return null
    }

    private fun calculateBreakDuration(session: BlockedProfileSessionEntity, profile: BlockedProfileEntity, at: Date): Double {
        if (profile.allowMultipleBreaks) {
            return session.usedBreakDurationIncludingActive(profile, at.time)
        }

        val breakStart = session.breakStartTimeEpochMilli ?: return 0.0
        val breakEnd = session.breakEndTimeEpochMilli
        if (breakEnd != null) {
            return maxOf(0.0, (breakEnd - breakStart).toDouble() / 1000.0)
        }

        val allowsBreaks = StrategyCapabilities.allowsTimedBreaks(profile.blockingStrategyId)
        if (session.isBreakActive(profile, allowsBreaks)) {
            return maxOf(0.0, (at.time - breakStart).toDouble() / 1000.0)
        }
        return 0.0
    }
}
