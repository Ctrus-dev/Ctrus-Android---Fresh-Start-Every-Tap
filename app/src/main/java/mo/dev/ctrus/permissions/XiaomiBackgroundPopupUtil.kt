package mo.dev.ctrus.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Xiaomi's "Display pop-up windows while running in the background" (MIUI/HyperOS). Without it,
 * MIUI refuses every activity Ctrus opens from the accessibility service (`Permission Denied
 * Activity` in logcat), so the block screen never shows and nothing looks blocked, though the
 * service is running. Seen on a POCO F2 Pro, where it gets turned off again with each USB install.
 *
 * Stored as the hidden app-op 10021, read like [XiaomiAutostartUtil]'s and gating the same things:
 * the welcome screen, starting a session (Home), a scheduled start, and its Settings row.
 */
object XiaomiBackgroundPopupUtil {
    private const val OP_BACKGROUND_START_ACTIVITY = 10021

    fun isApplicable(context: Context): Boolean = readMiuiAppOp(context, OP_BACKGROUND_START_ACTIVITY) != null

    /** True when it's allowed, or when this isn't a Xiaomi device / the state can't be read. */
    fun isGranted(context: Context): Boolean =
        readMiuiAppOp(context, OP_BACKGROUND_START_ACTIVITY)?.let { it == AppOpsManager.MODE_ALLOWED } ?: true

    /** Opens Ctrus's page in MIUI's permission editor, falling back to its App info screen. */
    fun request(context: Context) {
        val candidates = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", context.packageName),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
        )
        for (intent in candidates) {
            val launched = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (launched) return
        }
    }
}
