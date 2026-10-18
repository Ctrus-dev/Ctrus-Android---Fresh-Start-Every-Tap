package mo.dev.ctrus.blocking

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager

/**
 * Apps and sites that must never be blocked, whatever a profile says (including allow-mode,
 * where everything not selected is blocked). Mirrors Switchly's BlockSafetyPolicy/AppBlockSafety
 * (see AGENTS.md): without this a profile could lock the user out of the phone's Settings, its
 * Phone app, the launcher, or Ctrus itself, with no way back. Never blocked:
 * - Ctrus itself, and its sites (ctrus.pt and every *.ctrus.pt, e.g. recover.ctrus.pt, which is
 *   where a recovery code comes from).
 * - The phone's Settings and Phone apps. OEMs ship these under their own package names (Samsung's
 *   Phone app is com.samsung.android.dialer, not AOSP's com.android.dialer), so besides a list of
 *   known names this asks the OS which app actually handles Settings and which one is the default
 *   dialer on *this* device.
 * - The home launcher, system UI and the emergency/in-call screens.
 * - Every enabled keyboard (e.g. Gboard). Its window fires the same window-state event as an app
 *   when it opens, so in allow-mode typing in an allowed app showed the block screen for Gboard.
 * The app picker hides all of these, so they can't even be selected.
 */
object BlockSafetyPolicy {
    private val NEVER_BLOCK = setOf(
        "com.android.settings",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.phone",
        "com.android.incallui",
        "com.samsung.android.incallui",
        "com.android.systemui",
        "com.android.emergency",
    )

    private const val CTRUS_DOMAIN = "ctrus.pt"

    // isProtected() runs synchronously on every single TYPE_WINDOW_STATE_CHANGED event — an
    // AccessibilityService has a strict time budget to respond in, and resolveActivity() is a
    // binder IPC call to PackageManagerService. Re-issuing that IPC on every app switch was slow
    // enough on some OEMs (heavier system_server IPC contention, e.g. MIUI's security layer) to
    // make the service look unresponsive and get flagged/disabled as "malfunctioning" by Android's
    // own accessibility health check. The launcher/Settings/dialer essentially never change at
    // runtime, so they're resolved once and cached instead of on every event.
    @Volatile private var cachedResolvedPackages: Set<String>? = null

    fun isProtected(context: Context, packageName: String): Boolean {
        if (packageName == context.packageName) return true
        if (packageName in NEVER_BLOCK) return true
        if (isEnabledKeyboard(context, packageName)) return true
        return packageName in resolvedPackages(context)
    }

    // Not cached with the others: keyboards can be added or switched at any time. Settings.Secure
    // reads are served from a local cache, so this is cheap enough for every event.
    // ENABLED_INPUT_METHODS is "pkg/.Service:pkg2/.Service;subtype…".
    private fun isEnabledKeyboard(context: Context, packageName: String): Boolean {
        val resolver = context.contentResolver
        val ids = listOfNotNull(
            runCatching { Settings.Secure.getString(resolver, Settings.Secure.ENABLED_INPUT_METHODS) }.getOrNull(),
            runCatching { Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD) }.getOrNull(),
        ).joinToString(":")
        return ids.split(':').any { it.substringBefore('/') == packageName }
    }

    /** ctrus.pt itself or any subdomain of it. */
    fun isProtectedDomain(domain: String): Boolean {
        val normalized = domain.trim().lowercase().removePrefix("www.").trimEnd('.')
        return normalized == CTRUS_DOMAIN || normalized.endsWith(".$CTRUS_DOMAIN")
    }

    private fun resolvedPackages(context: Context): Set<String> {
        cachedResolvedPackages?.let { return it }
        val pm = context.packageManager
        fun handlerOf(intent: Intent): String? =
            runCatching { pm.resolveActivity(intent, 0)?.activityInfo?.packageName }.getOrNull()
        val resolved = setOfNotNull(
            handlerOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)),
            handlerOf(Intent(Settings.ACTION_SETTINGS)),
            handlerOf(Intent(Intent.ACTION_DIAL)),
            runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }.getOrNull(),
        ).filterNot { it == "android" } // the chooser, when no single default exists
            .toSet()
        cachedResolvedPackages = resolved
        return resolved
    }
}
