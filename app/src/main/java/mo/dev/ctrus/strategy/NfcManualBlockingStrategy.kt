package mo.dev.ctrus.strategy

import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.data.canUnblockWithNfcCode
import mo.dev.ctrus.data.hasPhysicalUnblockItem
import mo.dev.ctrus.util.UiText

/**
 * Mirrors NFCManualBlockingStrategy.swift ("Manual + Ctrus NFC"): instant manual start (the
 * session is tagged with the reserved [StrategyIds.MANUAL_START_TAG] since nothing was
 * scanned), but stopping requires an NFC scan — any tag works unless the profile has a
 * configured physical-unblock override, in which case the scan must match one of those.
 */
class NfcManualBlockingStrategy(private val sessions: SessionRepository) : BlockingStrategy {
    override val id = StrategyIds.NFC_MANUAL
    override val displayNameRes = R.string.strategy_nfc_manual_name
    override val descriptionRes = R.string.strategy_nfc_manual_description
    override val requiresSameCodeToStop = false
    override val startsManually = true

    override fun startRequirement(profile: BlockedProfileEntity) = StrategyRequirement.None

    override suspend fun startBlocking(profile: BlockedProfileEntity, input: StrategyInput, forceStart: Boolean): StrategyResult {
        val session = sessions.create(profile.id, tag = StrategyIds.MANUAL_START_TAG, forceStarted = forceStart)
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
