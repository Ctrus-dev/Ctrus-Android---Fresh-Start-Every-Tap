package mo.dev.ctrus.blocking

/**
 * What BlockingAccessibilityService decided to do about the package that just came
 * to the foreground. Kept separate from the service so the decision logic (which
 * profile is active, which packages it covers) can be unit tested without an
 * AccessibilityService instance.
 */
sealed interface BlockDecision {
    data object Allow : BlockDecision
    data class Block(val packageName: String, val profileName: String) : BlockDecision
}

/**
 * Placeholder decision source. Will be backed by the ported BlockedProfileSession /
 * StrategyManager equivalent — for now nothing is blocked, so the accessibility
 * service is inert until that lands.
 */
object BlockDecisionEngine {
    fun decide(context: android.content.Context, foregroundPackage: String): BlockDecision {
        if (BlockSafetyPolicy.isProtected(context, foregroundPackage)) {
            return BlockDecision.Allow
        }
        // TODO: check foregroundPackage against the active profile's blocked app list.
        return BlockDecision.Allow
    }
}
