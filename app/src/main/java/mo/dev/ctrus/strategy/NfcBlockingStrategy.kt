package mo.dev.ctrus.strategy

import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.data.canUnblockWithNfcCode
import mo.dev.ctrus.util.UiText

/**
 * Mirrors NFCBlockingStrategy.swift ("Ctrus NFC"): both start and stop require a physical tag
 * scan. To stop, either a configured physical-unblock override authorizes any tag, or — if none
 * is configured — the *same* tag used to start is required (waived for force-started sessions,
 * which have no "original" tag to match).
 */
class NfcBlockingStrategy(private val sessions: SessionRepository) : BlockingStrategy {
    override val id = StrategyIds.NFC
    override val displayNameRes = R.string.strategy_nfc_name
    override val descriptionRes = R.string.strategy_nfc_description
    override val requiresSameCodeToStop = true
    override val startsManually = false

    override fun startRequirement(profile: BlockedProfileEntity) = StrategyRequirement.ScanNfcTag

    override suspend fun startBlocking(profile: BlockedProfileEntity, input: StrategyInput, forceStart: Boolean): StrategyResult {
        if (forceStart) {
            val session = sessions.create(profile.id, tag = "force", forceStarted = true)
            return StrategyResult.Started(session.id)
        }
        val tag = (input as? StrategyInput.NfcTag)?.code
            ?: return StrategyResult.NeedsInput(StrategyRequirement.ScanNfcTag)
        val session = sessions.create(profile.id, tag = tag, forceStarted = false)
        return StrategyResult.Started(session.id)
    }

    override fun stopRequirement(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity) = StrategyRequirement.ScanNfcTag

    override suspend fun stopBlocking(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity, input: StrategyInput): StrategyResult {
        val tag = (input as? StrategyInput.NfcTag)?.code
            ?: return StrategyResult.NeedsInput(StrategyRequirement.ScanNfcTag)

        val authorized = profile.canUnblockWithNfcCode(tag) || session.forceStarted || tag == session.tag
        if (!authorized) {
            return StrategyResult.NeedsInput(StrategyRequirement.ScanNfcTag, UiText(R.string.error_original_tag_required))
        }

        sessions.endSession(session)
        return StrategyResult.Ended(profile.id)
    }
}
