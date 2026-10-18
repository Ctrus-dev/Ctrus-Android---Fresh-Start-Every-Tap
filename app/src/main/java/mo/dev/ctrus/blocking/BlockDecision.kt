package mo.dev.ctrus.blocking

/**
 * What BlockingAccessibilityService decided to do about the package that just came
 * to the foreground. Kept separate from the service so the decision logic can be unit
 * tested without an AccessibilityService instance.
 */
sealed interface BlockDecision {
    data object Allow : BlockDecision
    /** [siteDomain] is set when it's a website (open in browser [packageName]) being blocked, not the app itself. */
    data class Block(val packageName: String, val profileName: String, val siteDomain: String? = null) : BlockDecision

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

    // The stock AOSP names are kept as a fallback, but some OEMs (confirmed: MIUI) route the
    // uninstall-confirmation UI through their own package instead — hardcoding names silently
    // misses those. Resolved the same way BlockSafetyPolicy resolves the home launcher: ask the
    // OS which component actually handles the uninstall intent on *this* device, rather than
    // guessing, so this isn't specific to any one OEM.
    //
    // KNOWN LIMITATION (confirmed via `adb shell dumpsys window displays` on a MIUI/HyperOS POCO
    // F2 Pro): neither this resolution nor a hardcoded package list can ever catch MIUI's two most
    // common uninstall paths, because the confirmation isn't a separate installer app there at
    // all — it's a dialog rendered *inside* an already-protected/frequently-needed host:
    //   - Long-press icon -> Uninstall: mFocusedApp stays the launcher itself the whole time
    //     (observed: com.mi.android.globallauncher/com.miui.home.launcher.Launcher) — already in
    //     BlockSafetyPolicy's never-block set, so decide() short-circuits to Allow before this
    //     check even runs.
    //   - Settings -> Apps -> [app] -> Uninstall: mFocusedApp is MIUI's own Security Center app
    //     page (observed: com.miui.securitycenter/com.miui.appmanager.ApplicationsDetailsActivity),
    //     which isn't safe to add here either — that same package/activity also hosts ordinary,
    //     unrelated Settings screens (permissions, app info, etc.), so matching it would kick the
    //     user out of all of those while strict mode is on, not just an uninstall attempt.
    // Catching this would require inspecting on-screen *content* (e.g. dialog text) rather than
    // just the foreground package/activity — meaningfully more fragile (language/MIUI-version
    // dependent) and not attempted here; decided with the user to accept this as a known gap for
    // now rather than chase a fragile heuristic. Revisit if it becomes a priority again.
    private val KNOWN_PACKAGE_INSTALLER_PACKAGES = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")
    @Volatile private var cachedResolvedPackageInstaller: String? = null
    @Volatile private var resolvedPackageInstallerAttempted = false

    fun decide(context: android.content.Context, foregroundPackage: String): BlockDecision {
        if (BlockSafetyPolicy.isProtected(context, foregroundPackage)) {
            return BlockDecision.Allow
        }
        // Nothing is blocked on the lock screen: whatever opens there is part of unlocking,
        // a call, an alarm or the camera, and the block screen (showWhenLocked) would cover
        // the unlock itself — that locked a Galaxy A56 out when its fingerprint screen was
        // blocked. Apps get checked again once the phone is unlocked.
        if (isLocked(context)) return BlockDecision.Allow

        val state = BlockingStateHolder.state.value
        if (state.isInstallBlockActive && foregroundPackage in PLAY_STORE_PACKAGES) {
            return BlockDecision.Dismiss
        }
        if (state.isDeletionBlockActive && isPackageInstallerSurface(context, foregroundPackage)) {
            return BlockDecision.Dismiss
        }
        // Only apps with a launcher icon (the ones the picker offers) can be blocked. In
        // allow-mode everything unselected is blocked, which used to include system parts that
        // open their own windows, e.g. Samsung's fingerprint screen (seen on One UI 8).
        return if (state.isPackageBlocked(foregroundPackage) && isLaunchable(context, foregroundPackage)) {
            BlockDecision.Block(packageName = foregroundPackage, profileName = state.profileName)
        } else {
            BlockDecision.Allow
        }
    }

    /**
     * Per-site blocking: called with the host currently open in a supported browser (see
     * [BrowserUrlReader]). The browser itself being allowed is a precondition, so this only runs
     * after [decide] returned Allow for it.
     */
    fun decideSite(browserPackage: String, host: String): BlockDecision {
        val state = BlockingStateHolder.state.value
        return if (state.isDomainBlocked(host)) {
            BlockDecision.Block(packageName = browserPackage, profileName = state.profileName, siteDomain = host)
        } else {
            BlockDecision.Allow
        }
    }

    private fun isLocked(context: android.content.Context): Boolean =
        runCatching { context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true }.getOrDefault(false)

    // Launcher icons rarely change, and this runs on every window event: one lookup per package.
    private val launchableByPackage = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun isLaunchable(context: android.content.Context, packageName: String): Boolean =
        launchableByPackage.getOrPut(packageName) {
            runCatching { context.packageManager.getLaunchIntentForPackage(packageName) != null }.getOrDefault(true)
        }

    private fun isPackageInstallerSurface(context: android.content.Context, foregroundPackage: String): Boolean {
        if (foregroundPackage in KNOWN_PACKAGE_INSTALLER_PACKAGES) return true
        return foregroundPackage == resolvedPackageInstallerPackage(context)
    }

    private fun resolvedPackageInstallerPackage(context: android.content.Context): String? {
        if (resolvedPackageInstallerAttempted) return cachedResolvedPackageInstaller
        resolvedPackageInstallerAttempted = true
        val uninstallIntent = android.content.Intent(android.content.Intent.ACTION_DELETE)
            .setData(android.net.Uri.fromParts("package", "mo.dev.ctrus.placeholder", null))
        cachedResolvedPackageInstaller = context.packageManager.resolveActivity(uninstallIntent, 0)?.activityInfo?.packageName
        return cachedResolvedPackageInstaller
    }
}
