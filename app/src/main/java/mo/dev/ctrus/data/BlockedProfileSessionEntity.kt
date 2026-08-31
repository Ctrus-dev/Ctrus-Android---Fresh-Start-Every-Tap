package mo.dev.ctrus.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/** Room equivalent of Ctrus/Models/BlockedProfileSessions.swift. */
@Entity(
    tableName = "blocked_profile_sessions",
    foreignKeys = [
        ForeignKey(
            entity = BlockedProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("endTimeEpochMilli")],
)
data class BlockedProfileSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val profileId: String,
    val startTimeEpochMilli: Long = Instant.now().toEpochMilli(),
    val endTimeEpochMilli: Long? = null,
    val breakStartTimeEpochMilli: Long? = null,
    val breakEndTimeEpochMilli: Long? = null,
    val usedBreakDurationInSeconds: Double = 0.0,
    val forceStarted: Boolean = false,
)

/**
 * Pure session-state helpers, ported from the computed properties on BlockedProfileSession.swift.
 * Pause (pauseStartTime/pauseEndTime on iOS) isn't ported: it's only ever set by
 * NFCPauseTimerBlockingStrategy, which — like the session-timer/soft-unblock strategies — isn't
 * one of the two strategies `StrategyManager.pickerStrategies` actually offers, so it's
 * unreachable for a real user on iOS too.
 */
val BlockedProfileSessionEntity.isActive: Boolean get() = endTimeEpochMilli == null

fun BlockedProfileSessionEntity.duration(now: Long = System.currentTimeMillis()): Long =
    (endTimeEpochMilli ?: now) - startTimeEpochMilli

fun BlockedProfileSessionEntity.isBreakActive(profile: BlockedProfileEntity, allowsTimedBreaks: Boolean): Boolean =
    profile.enableBreaks && allowsTimedBreaks && breakStartTimeEpochMilli != null && breakEndTimeEpochMilli == null

fun BlockedProfileSessionEntity.isBreakAvailable(profile: BlockedProfileEntity, allowsTimedBreaks: Boolean, now: Long = System.currentTimeMillis()): Boolean {
    if (!profile.enableBreaks || !allowsTimedBreaks) return false
    return if (profile.allowMultipleBreaks) {
        remainingBreakAllowance(profile, now) > 0
    } else {
        breakEndTimeEpochMilli == null
    }
}

fun BlockedProfileSessionEntity.totalBreakAllowanceSeconds(profile: BlockedProfileEntity): Double =
    (profile.breakTimeInMinutes * 60).toDouble()

fun BlockedProfileSessionEntity.activeBreakElapsedSeconds(now: Long = System.currentTimeMillis()): Double {
    val start = breakStartTimeEpochMilli ?: return 0.0
    if (breakEndTimeEpochMilli != null) return 0.0
    return ((now - start).coerceAtLeast(0)).toDouble() / 1000.0
}

fun BlockedProfileSessionEntity.usedBreakDurationIncludingActive(profile: BlockedProfileEntity, now: Long = System.currentTimeMillis()): Double {
    if (profile.allowMultipleBreaks) {
        return minOf(totalBreakAllowanceSeconds(profile), usedBreakDurationInSeconds + activeBreakElapsedSeconds(now))
    }
    val start = breakStartTimeEpochMilli ?: return 0.0
    val end = breakEndTimeEpochMilli
    return if (end != null) {
        (end - start).coerceAtLeast(0).toDouble() / 1000.0
    } else {
        (now - start).coerceAtLeast(0).toDouble() / 1000.0
    }
}

fun BlockedProfileSessionEntity.remainingBreakAllowance(profile: BlockedProfileEntity, now: Long = System.currentTimeMillis()): Double =
    (totalBreakAllowanceSeconds(profile) - usedBreakDurationIncludingActive(profile, now)).coerceAtLeast(0.0)
