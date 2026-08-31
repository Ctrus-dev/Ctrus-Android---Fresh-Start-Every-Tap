package mo.dev.ctrus.strategy

import androidx.annotation.StringRes
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.BlockedProfileSessionEntity

/**
 * Kotlin equivalent of the `BlockingStrategy` protocol in Ctrus/Models/Strategies/BlockingStrategy.swift,
 * trimmed to the fields that actually differ between the two strategies the app exposes
 * (`Ctrus NFC` and `Manual + Ctrus NFC` — see [StrategyIds]). Where iOS returns an optional
 * `any View` sheet from start/stop, this splits that into two steps: [startRequirement]/
 * [stopRequirement] tell the caller what input to collect (currently only ever an NFC scan),
 * then [startBlocking]/[stopBlocking] run once that input is in hand.
 */
interface BlockingStrategy {
    val id: String
    @get:StringRes val displayNameRes: Int
    @get:StringRes val descriptionRes: Int

    /** True only for Ctrus NFC — stopping must use the exact tag that started the session. */
    val requiresSameCodeToStop: Boolean

    /** True only for Manual + Ctrus NFC — starting needs no scan. */
    val startsManually: Boolean

    fun startRequirement(profile: BlockedProfileEntity): StrategyRequirement
    suspend fun startBlocking(profile: BlockedProfileEntity, input: StrategyInput, forceStart: Boolean): StrategyResult

    fun stopRequirement(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity): StrategyRequirement
    suspend fun stopBlocking(profile: BlockedProfileEntity, session: BlockedProfileSessionEntity, input: StrategyInput): StrategyResult
}
