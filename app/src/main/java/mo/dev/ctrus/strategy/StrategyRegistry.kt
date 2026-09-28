package mo.dev.ctrus.strategy

import mo.dev.ctrus.data.SessionRepository

/**
 * The strategies the profile form offers (StrategyManager.pickerStrategies plus the
 * separately-listed ScheduleBlockingStrategy row) — see [StrategyIds] for
 * why the other iOS strategy classes weren't ported.
 */
class StrategyRegistry(sessions: SessionRepository) {
    private val byId: Map<String, BlockingStrategy> = listOf(
        NfcBlockingStrategy(sessions),
        NfcManualBlockingStrategy(sessions),
        ScheduleBlockingStrategy(sessions),
    ).associateBy { it.id }

    val pickerStrategies: List<BlockingStrategy> = StrategyIds.PICKER_IDS.mapNotNull { byId[it] }

    fun get(id: String?): BlockingStrategy = byId[id] ?: byId.getValue(StrategyIds.NFC)
}
