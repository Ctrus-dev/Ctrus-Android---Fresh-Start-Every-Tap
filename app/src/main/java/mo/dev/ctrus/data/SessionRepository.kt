package mo.dev.ctrus.data

import kotlinx.coroutines.flow.Flow
import mo.dev.ctrus.scheduling.SchedulingGateway
import java.time.Instant

/** Mirrors the static + instance methods on BlockedProfileSessions.swift, backed by Room. */
class SessionRepository(private val dao: BlockedProfileSessionDao, private val scheduling: SchedulingGateway) {
    fun observeMostRecentActive(): Flow<BlockedProfileSessionEntity?> = dao.observeMostRecentActive()

    suspend fun mostRecentActive(): BlockedProfileSessionEntity? = dao.getMostRecentActive()

    suspend fun find(id: String): BlockedProfileSessionEntity? = dao.getById(id)

    fun observeForProfile(profileId: String): Flow<List<BlockedProfileSessionEntity>> = dao.observeForProfile(profileId)

    /** Every session of every profile, newest first. Backs the combined "Profiles" insights. */
    fun observeAll(): Flow<List<BlockedProfileSessionEntity>> = dao.observeAll()

    suspend fun recentInactive(limit: Int = 50): List<BlockedProfileSessionEntity> = dao.getRecentInactive(limit)

    suspend fun create(profileId: String, tag: String, forceStarted: Boolean = false): BlockedProfileSessionEntity {
        val session = BlockedProfileSessionEntity(profileId = profileId, tag = tag, forceStarted = forceStarted)
        dao.upsert(session)
        return session
    }

    suspend fun endSession(session: BlockedProfileSessionEntity): BlockedProfileSessionEntity {
        val ended = session.copy(endTimeEpochMilli = Instant.now().toEpochMilli())
        dao.update(ended)
        // A session can end (strategy stop, emergency unblock) while a break's expiry/warning
        // alarms are still pending — without this, e.g. the "1 minute left" notification fires
        // later regardless, well after the session it refers to no longer exists.
        scheduling.cancelBreakExpiry(session.id)
        scheduling.cancelBreakWarning(session.id)
        return ended
    }

    suspend fun startBreak(session: BlockedProfileSessionEntity, allowMultipleBreaks: Boolean): BlockedProfileSessionEntity {
        val now = Instant.now().toEpochMilli()
        val updated = if (allowMultipleBreaks) {
            session.copy(breakStartTimeEpochMilli = now, breakEndTimeEpochMilli = null)
        } else {
            session.copy(breakStartTimeEpochMilli = now)
        }
        dao.update(updated)
        return updated
    }

    /**
     * Records the break as over at [now] — by default the current time, but an expired break is
     * closed at its scheduled end instead (see [finalizeExpiredBreak]) so a late alarm doesn't
     * inflate break time in Insights.
     */
    suspend fun endBreak(
        session: BlockedProfileSessionEntity,
        profile: BlockedProfileEntity,
        now: Long = Instant.now().toEpochMilli(),
    ): BlockedProfileSessionEntity {
        val completedSeconds = session.activeBreakElapsedSeconds(now)
        val updated = if (profile.allowMultipleBreaks) {
            session.copy(
                breakEndTimeEpochMilli = now,
                usedBreakDurationInSeconds = minOf(
                    session.totalBreakAllowanceSeconds(profile),
                    session.usedBreakDurationInSeconds + completedSeconds,
                ),
            )
        } else {
            session.copy(breakEndTimeEpochMilli = now)
        }
        dao.update(updated)
        return updated
    }

    suspend fun update(session: BlockedProfileSessionEntity) = dao.update(session)

    /**
     * Closes a break whose time is already up, stamped at its scheduled end. Safe to call any
     * time from anywhere (expiry alarm, session ticker, accessibility service); it does nothing
     * unless that break is really overdue.
     */
    suspend fun finalizeExpiredBreak(sessionId: String, profile: BlockedProfileEntity, now: Long = Instant.now().toEpochMilli()) {
        val session = dao.getById(sessionId) ?: return
        if (session.endTimeEpochMilli != null) return
        val scheduledEnd = session.breakScheduledEndMillis(profile) ?: return
        if (now < scheduledEnd) return
        endBreak(session, profile, now = scheduledEnd)
        scheduling.cancelBreakExpiry(session.id)
        scheduling.cancelBreakWarning(session.id)
    }

}
