package mo.dev.ctrus.strategy

import mo.dev.ctrus.data.SessionRepository

/**
 * The two strategies StrategyManager.pickerStrategies actually offers — see [StrategyIds] for
 * why the other iOS strategy classes weren't ported.
 */
class StrategyRegistry(sessions: SessionRepository) {
    private val byId: Map<String, BlockingStrategy> = listOf(
        NfcBlockingStrategy(sessions),
        NfcManualBlockingStrategy(sessions),
    ).associateBy { it.id }

    val pickerStrategies: List<BlockingStrategy> = StrategyIds.PICKER_IDS.mapNotNull { byId[it] }

    fun get(id: String?): BlockingStrategy = byId[id] ?: byId.getValue(StrategyIds.NFC)
}
