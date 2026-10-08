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
    /** When the running break is due to end; it stops counting from then on, even before Room catches up. */
    val breakEndsAtEpochMilli: Long? = null,
    val enableStrictMode: Boolean = false,
    val enableBlockAppInstallation: Boolean = false,
) {
    val isBlocking: Boolean get() = profileId != null

    /** Time-based, not just the stored flag: a break whose time is up no longer pauses blocking. */
    fun isBreakActiveAt(now: Long = System.currentTimeMillis()): Boolean =
        isBreakActive && (breakEndsAtEpochMilli == null || now < breakEndsAtEpochMilli)

    // Mirrors AppBlockerUtil.deactivateRestrictionsForBreak: app/domain shielding and the
    // install block both pause for a break, but the deletion block (denyAppRemoval on iOS)
    // deliberately does not — see its "strict mode" comment there.
    val isInstallBlockActive: Boolean get() = isBlocking && !isBreakActiveAt() && enableBlockAppInstallation
    val isDeletionBlockActive: Boolean get() = isBlocking && enableStrictMode

    fun isPackageBlocked(packageName: String): Boolean {
        if (!isBlocking || isBreakActiveAt()) return false
        return if (allowMode) packageName !in blockedPackages else packageName in blockedPackages
    }

    fun isDomainBlocked(domain: String): Boolean {
        if (!isBlocking || isBreakActiveAt()) return false
        // Ctrus's own sites stay reachable even in allow-mode (see BlockSafetyPolicy).
        if (BlockSafetyPolicy.isProtectedDomain(domain)) return false
        // "Block Adult Websites" is its own switch, independent of "Block Websites in Browser"
        // (which only governs the profile's own domain list), like iOS's separate filter.
        if (enableAdultContentBlocking && AdultDomainList.contains(domain)) return true
        if (!enableBrowserBlocking) return false
        // "www." is ignored on both sides: browsers often hide it (Chrome shows "youtube.com"),
        // so "www.youtube.com" in a profile must still match, and vice versa.
        val host = domain.lowercase().removePrefix("www.")
        val matches = domains.any {
            val blocked = it.lowercase().removePrefix("www.")
            host == blocked || host.endsWith(".$blocked")
        }
        return if (allowModeDomains) !matches else matches
    }
}
