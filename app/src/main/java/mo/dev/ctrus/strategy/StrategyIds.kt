package mo.dev.ctrus.strategy

/**
 * Stable ids for the blocking strategies, stored in [mo.dev.ctrus.data.BlockedProfileEntity.blockingStrategyId].
 * Mirrors `static let id` on the strategy conformers in Ctrus/Models/Strategies. Only the two
 * strategies `StrategyManager.pickerStrategies` actually offers when creating/editing a profile
 * are ported — `ManualBlockingStrategy`, `NFCTimerBlockingStrategy`, `NFCPauseTimerBlockingStrategy`,
 * `NFCSoftUnblockBlockingStrategy`, and `ShortcutTimerBlockingStrategy` exist in the iOS source but
 * are never selectable through the app's UI (internal-only/legacy), so they were deliberately not
 * ported.
 */
object StrategyIds {
    /** "Ctrus NFC" — NFC required to both start and stop. */
    const val NFC = "nfc"

    /** "Manual + Ctrus NFC" — manual start, NFC required to stop. */
    const val NFC_MANUAL = "nfc_manual"

    /**
     * Reserved session tag (not a selectable strategy) for sessions [NFC_MANUAL] starts —
     * mirrors iOS reusing `ManualBlockingStrategy.id` purely as a tag value for the same case,
     * even though that strategy itself is never user-facing.
     */
    const val MANUAL_START_TAG = "manual"

    val PICKER_IDS = listOf(NFC, NFC_MANUAL)
}
