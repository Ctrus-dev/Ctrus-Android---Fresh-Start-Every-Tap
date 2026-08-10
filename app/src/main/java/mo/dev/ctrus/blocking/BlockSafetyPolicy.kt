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

    fun isProtected(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        if (packageName in NEVER_BLOCK) return true
        return isDefaultHomeLauncher(context, packageName)
    }

    private fun isDefaultHomeLauncher(context: Context, packageName: String): Boolean {
        val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(homeIntent, 0)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
}
