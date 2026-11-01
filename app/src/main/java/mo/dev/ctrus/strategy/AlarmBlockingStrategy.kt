package mo.dev.ctrus.strategy

import mo.dev.ctrus.R
import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.data.canUnblockWithNfcCode
import mo.dev.ctrus.data.hasPhysicalUnblockItem
import mo.dev.ctrus.util.UiText

/**
 * Mirrors AlarmBlockingStrategy.swift ("Alarm + Ctrus NFC", spec D1): starts on its own like
 * [ScheduleBlockingStrategy], but instead of ending at the end time an alarm rings
 * ([mo.dev.ctrus.alarm.WakeAlarmManager]) that only the Ctrus or an emergency unblock silences —
 * both of which end the session. Never started by hand.
 */
class AlarmBlockingStrategy(private val sessions: SessionRepository) : BlockingStrategy {
    override val id = StrategyIds.ALARM
    override val displayNameRes = R.string.strategy_alarm_name
    override val descriptionRes = R.string.strategy_alarm_description
    override val requiresSameCodeToStop = false
    override val startsManually = false

    override fun startRequirement(profile: BlockedProfileEntity) = StrategyRequirement.None

    override suspend fun startBlocking(profile: BlockedProfileEntity, input: StrategyInput, forceStart: Boolean): StrategyResult =
        StrategyResult.Error(UiText(R.string.error_schedule_manual_start))

    override fun stopRequirement(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity) = StrategyRequirement.ScanNfcTag

    override suspend fun stopBlocking(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity, input: StrategyInput): StrategyResult {
        val scanned = input as? StrategyInput.NfcTag
            ?: return StrategyResult.NeedsInput(StrategyRequirement.ScanNfcTag)

        // Child Mode: only the parents' Ctrus, or an allowed child's one, ends a session.
        if (ChildModeStore.isChildMode) {
            if (!profile.childModeAllowsEnding(scanned.uid)) {
                return StrategyResult.NeedsInput(StrategyRequirement.ScanNfcTag, UiText(R.string.error_not_allowed_to_unblock))
            }
            sessions.endSession(session)
            return StrategyResult.Ended(profile.id)
        }

        // A wrong tag leaves the alarm ringing and the session running.
        val authorized = !profile.hasPhysicalUnblockItem() || profile.canUnblockWithNfcCode(scanned.code)
        if (!authorized) {
            return StrategyResult.NeedsInput(StrategyRequirement.ScanNfcTag, UiText(R.string.error_not_allowed_to_unblock))
        }

        // Silencing the alarm, the next occurrence's alarm and a one-time profile's deletion
        // follow the session's end, however it ended — see WakeAlarmManager.onSessionEnded.
        sessions.endSession(session)
        return StrategyResult.Ended(profile.id)
    }
}
