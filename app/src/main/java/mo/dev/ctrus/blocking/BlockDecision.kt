package mo.dev.ctrus.blocking

/**
 * What BlockingAccessibilityService decided to do about the package that just came
 * to the foreground. Kept separate from the service so the decision logic can be unit
 * tested without an AccessibilityService instance.
 */
sealed interface BlockDecision {
    data object Allow : BlockDecision
    data class Block(val packageName: String, val profileName: String) : BlockDecision
}

/**
 * Real decision source, backed by [BlockingStateHolder]'s in-memory snapshot of the active
 * session's profile (itself derived from Room — see BlockingStateHolder's kdoc). Mirrors the
 * deny-list/allow-list resolution AppBlockerUtil.swift does against ManagedSettingsStore.
 */
object BlockDecisionEngine {
    fun decide(context: android.content.Context, foregroundPackage: String): BlockDecision {
        if (BlockSafetyPolicy.isProtected(context, foregroundPackage)) {
            return BlockDecision.Allow
        }

        val state = BlockingStateHolder.state.value
        return if (state.isPackageBlocked(foregroundPackage)) {
            BlockDecision.Block(packageName = foregroundPackage, profileName = state.profileName)
        } else {
            BlockDecision.Allow
        }
    }
}
