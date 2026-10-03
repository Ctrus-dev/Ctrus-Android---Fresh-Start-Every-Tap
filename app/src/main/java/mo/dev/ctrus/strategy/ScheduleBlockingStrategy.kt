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
 * at the profile's [mo.dev.ctrus.data.ProfileSchedule] via [mo.dev.ctrus.scheduling.ScheduleAlarmScheduler],
 * which creates the session directly. Unlike iOS (`startsManually = true`), they can't be started
 * by hand: Home hides/disables the start gestures for these profiles, and [startBlocking] refuses as
 * a backstop. Stopping follows the same NFC rules as [NfcManualBlockingStrategy], and a fixed
 * duration also stops the session automatically.
 */
class ScheduleBlockingStrategy(private val sessions: SessionRepository) : BlockingStrategy {
    override val id = StrategyIds.SCHEDULE
    override val displayNameRes = R.string.strategy_schedule_name
    override val descriptionRes = R.string.strategy_schedule_description
    override val requiresSameCodeToStop = false
    override val startsManually = false

    override fun startRequirement(profile: BlockedProfileEntity) = StrategyRequirement.None

    override suspend fun startBlocking(profile: BlockedProfileEntity, input: StrategyInput, forceStart: Boolean): StrategyResult =
        StrategyResult.Error(UiText(R.string.error_schedule_manual_start))

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
