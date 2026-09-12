package mo.dev.ctrus.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Discoverable, opt-in troubleshooting link for Settings — not a mandatory checklist item like an
 * earlier revision had. BlockingAccessibilityService already verifies and retries showing the
 * block screen on its own (see its kdoc, mirroring Switchly's BlockLaunchController), which
 * silently fixes most OEM-specific "start activity from background" restrictions without the user
 * ever needing to know they exist. But that retry assumes the restriction is a soft one (the
 * block screen loses a focus race) — on a hard block (confirmed: some MIUI/HyperOS versions),
 * every attempt is refused and only a manually-granted OEM permission fixes it. There's no way to
 * detect that case from code, so this just gives an escape hatch for whoever hits it, framed
 * generically since the exact OEM/permission name isn't knowable in general — [openSettings]
 * only has a concrete deep link for Xiaomi (the one OEM this has actually been confirmed on);
 * every other manufacturer falls back to the plain app-info page.
 */
object BackgroundLaunchHelpUtil {
    fun openSettings(context: Context) {
        if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", context.packageName)
            }
            if (runCatching { context.startActivity(miuiIntent) }.isSuccess) return
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(fallback) }
    }
}
