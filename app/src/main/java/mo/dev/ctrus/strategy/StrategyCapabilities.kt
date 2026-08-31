package mo.dev.ctrus.strategy

/**
 * Cheap capability lookup by strategy id. Both ported strategies allow timed breaks (the
 * BlockingStrategy protocol's default is `true`, and neither NFCBlockingStrategy nor
 * NFCManualBlockingStrategy overrides it) — kept as a lookup rather than a hardcoded `true` so
 * a future strategy that opts out doesn't require touching every call site.
 */
object StrategyCapabilities {
    fun allowsTimedBreaks(strategyId: String?): Boolean = true
}
