package mo.dev.ctrus.strategy

import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.data.canUnblockWithNfcCode
import mo.dev.ctrus.data.hasPhysicalUnblockItem
import mo.dev.ctrus.util.UiText

/**
 * Mirrors ScheduleBlockingStrategy.swift ("Schedule + Ctrus NFC"): sessions start automatically
 * at the profile's [mo.dev.ctrus.data.ProfileSchedule] via [mo.dev.ctrus.scheduling.ScheduleAlarmScheduler]
 * (and can still be started from the app's session bubble, like iOS's `startsManually = true`),
 * but never end on their own — stopping always needs an NFC scan, same rules as
 * [NfcManualBlockingStrategy].
 */
class ScheduleBlockingStrategy(private val sessions: SessionRepository) : BlockingStrategy {
    override val id = StrategyIds.SCHEDULE
    override val displayNameRes = R.string.strategy_schedule_name
    override val descriptionRes = R.string.strategy_schedule_description
    override val requiresSameCodeToStop = false
    override val startsManually = true

    override fun startRequirement(profile: BlockedProfileEntity) = StrategyRequirement.None

    override suspend fun startBlocking(profile: BlockedProfileEntity, input: StrategyInput, forceStart: Boolean): StrategyResult {
        val session = sessions.create(profile.id, tag = StrategyIds.SCHEDULE, forceStarted = forceStart)
        return StrategyResult.Started(session.id)
    }

    override fun stopRequirement(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity) = StrategyRequirement.ScanNfcTag

    override suspend fun stopBlocking(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity, input: StrategyInput): StrategyResult {
        val tag = (input as? StrategyInput.NfcTag)?.code
            ?: return StrategyResult.NeedsInput(StrategyRequirement.ScanNfcTag)

        val authorized = !profile.hasPhysicalUnblockItem() || profile.canUnblockWithNfcCode(tag)
        if (!authorized) {
            return StrategyResult.NeedsInput(StrategyRequirement.ScanNfcTag, UiText(R.string.error_not_allowed_to_unblock))
        }

        sessions.endSession(session)
        return StrategyResult.Ended(profile.id)
    }
}
