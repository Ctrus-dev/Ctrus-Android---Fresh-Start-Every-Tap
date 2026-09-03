package mo.dev.ctrus.blocking

/**
 * What BlockingAccessibilityService decided to do about the package that just came
 * to the foreground. Kept separate from the service so the decision logic can be unit
 * tested without an AccessibilityService instance.
 */
sealed interface BlockDecision {
    data object Allow : BlockDecision
    data class Block(val packageName: String, val profileName: String) : BlockDecision

    /** Just leave the interrupted surface (Play Store, an uninstall confirmation) — showing the
     *  citrus shield screen there would be a jarring mismatch, since the user didn't pick that
     *  surface as something to block, only an action it happens to perform. */
    data object Dismiss : BlockDecision
}

/**
 * Real decision source, backed by [BlockingStateHolder]'s in-memory snapshot of the active
 * session's profile (itself derived from Room — see BlockingStateHolder's kdoc). Mirrors the
 * deny-list/allow-list resolution AppBlockerUtil.swift does against ManagedSettingsStore.
 */
object BlockDecisionEngine {
    // iOS enforces denyAppInstallation/denyAppRemoval at the OS level via ManagedSettingsStore —
    // there's no third-party equivalent on Android. The closest reachable approximation is
    // intercepting the two system surfaces that actually perform those actions: the Play Store
    // (installs) and the package installer's uninstall-confirmation UI (deletions, for any app,
    // matching "including Ctrus" in field_prevent_app_deletion_desc). Neither package is in
    // BlockSafetyPolicy's NEVER_BLOCK set, so this can't strand the user out of Settings/dialer/home.
    private val PLAY_STORE_PACKAGES = setOf("com.android.vending")
    private val PACKAGE_INSTALLER_PACKAGES = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")

    fun decide(context: android.content.Context, foregroundPackage: String): BlockDecision {
        if (BlockSafetyPolicy.isProtected(context, foregroundPackage)) {
            return BlockDecision.Allow
        }

        val state = BlockingStateHolder.state.value
        if (state.isInstallBlockActive && foregroundPackage in PLAY_STORE_PACKAGES) {
            return BlockDecision.Dismiss
        }
        if (state.isDeletionBlockActive && foregroundPackage in PACKAGE_INSTALLER_PACKAGES) {
            return BlockDecision.Dismiss
        }
        return if (state.isPackageBlocked(foregroundPackage)) {
            BlockDecision.Block(packageName = foregroundPackage, profileName = state.profileName)
        } else {
            BlockDecision.Allow
        }
    }
}
