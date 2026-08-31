package mo.dev.ctrus.strategy

import mo.dev.ctrus.util.UiText

/**
 * What extra input a [BlockingStrategy] needs collected by the caller before start/stop can
 * actually run. Only NFC scanning is modeled — the only two strategies actually offered by the
 * app (`Ctrus NFC`, `Manual + Ctrus NFC`) either need no input or need a tag scan; there's no
 * duration/pause/soft-unblock picker to model since those strategies aren't user-selectable in
 * the iOS app either.
 */
sealed interface StrategyRequirement {
    data object None : StrategyRequirement
    data object ScanNfcTag : StrategyRequirement
}

sealed interface StrategyInput {
    data object None : StrategyInput
    data class NfcTag(val code: String) : StrategyInput
}

sealed interface StrategyResult {
    data class Started(val sessionId: String) : StrategyResult
    data class Ended(val profileId: String) : StrategyResult
    data class Error(val message: UiText) : StrategyResult
    /** Input collected so far wasn't enough (e.g. wrong NFC tag) — caller should re-prompt. */
    data class NeedsInput(val requirement: StrategyRequirement, val message: UiText? = null) : StrategyResult
}
