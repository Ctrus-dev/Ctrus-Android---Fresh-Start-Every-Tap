package mo.dev.ctrus.blocking

import android.content.Context

/**
 * Packages that must never be blocked, regardless of profile configuration — mirrors
 * Switchly's BlockSafetyPolicy/AppBlockSafety (see AGENTS.md). Without this, a
 * misconfigured profile could lock the user out of Settings, the dialer, or Ctrus
 * itself, with no way to recover.
 */
object BlockSafetyPolicy {
    private val NEVER_BLOCK = setOf(
        "com.android.settings",
        "com.android.dialer",
        "com.android.phone",
        "com.android.systemui",
        "com.android.emergency",
    )

    // isProtected() runs synchronously on every single TYPE_WINDOW_STATE_CHANGED event — an
    // AccessibilityService has a strict time budget to respond in, and resolveActivity() is a
    // binder IPC call to PackageManagerService. Re-issuing that IPC on every app switch was slow
    // enough on some OEMs (heavier system_server IPC contention, e.g. MIUI's security layer) to
    // make the service look unresponsive and get flagged/disabled as "malfunctioning" by Android's
    // own accessibility health check. The default home launcher essentially never changes at
    // runtime, so resolve it once and cache it instead of on every event.
    @Volatile private var cachedHomeLauncherPackage: String? = null

    fun isProtected(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        if (packageName in NEVER_BLOCK) return true
        return packageName == homeLauncherPackage(context)
    }

    private fun homeLauncherPackage(context: Context): String? {
        cachedHomeLauncherPackage?.let { return it }
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(homeIntent, 0)?.activityInfo?.packageName
        cachedHomeLauncherPackage = resolved
        return resolved
    }
}
