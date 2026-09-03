package mo.dev.ctrus.blocking

/**
 * Snapshot of "what's currently blocked", kept in memory by [BlockingStateHolder] so the
 * AccessibilityService's synchronous event callback never has to touch Room directly.
 * This is the in-process replacement for iOS's App-Group `SharedData` snapshot layer (see the
 * port plan) — a cache, not a second source of truth: it's always derived from Room.
 */
data class BlockingState(
    val profileId: String? = null,
    val profileName: String = "",
    val blockingStrategyId: String? = null,
    val sessionId: String? = null,
    val blockedPackages: Set<String> = emptySet(),
    val allowMode: Boolean = false,
    val domains: Set<String> = emptySet(),
    val allowModeDomains: Boolean = false,
    val enableBrowserBlocking: Boolean = true,
    val enableAdultContentBlocking: Boolean = false,
    val isBreakActive: Boolean = false,
    val enableStrictMode: Boolean = false,
    val enableBlockAppInstallation: Boolean = false,
) {
    val isBlocking: Boolean get() = profileId != null

    // Mirrors AppBlockerUtil.deactivateRestrictionsForBreak: app/domain shielding and the
    // install block both pause for a break, but the deletion block (denyAppRemoval on iOS)
    // deliberately does not — see its "strict mode" comment there.
    val isInstallBlockActive: Boolean get() = isBlocking && !isBreakActive && enableBlockAppInstallation
    val isDeletionBlockActive: Boolean get() = isBlocking && enableStrictMode

    fun isPackageBlocked(packageName: String): Boolean {
        if (!isBlocking || isBreakActive) return false
        return if (allowMode) packageName !in blockedPackages else packageName in blockedPackages
    }

    fun isDomainBlocked(domain: String): Boolean {
        if (!isBlocking || isBreakActive || !enableBrowserBlocking) return false
        val matches = domains.any { domain == it || domain.endsWith(".$it") }
        return if (allowModeDomains) !matches else matches
    }
}
