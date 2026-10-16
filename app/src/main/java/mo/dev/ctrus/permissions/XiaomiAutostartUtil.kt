package mo.dev.ctrus.permissions

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Xiaomi's "Autostart" (MIUI/HyperOS, so Xiaomi, Redmi and POCO). MIUI force-stops apps when the
 * screen locks ("AutoLockOffClean") and, with Autostart off, won't let the system start them
 * again — including to rebind [mo.dev.ctrus.blocking.BlockingAccessibilityService]. The service
 * then stays "on" in Accessibility settings but shows "This service is malfunctioning", and
 * nothing is blocked until the user opens Ctrus. Seen on a POCO F2 Pro overnight during a
 * scheduled session, with the battery-optimization exemption already granted.
 *
 * There's no public API for it: MIUI stores it as the hidden app-op 10008, which Xiaomi's own
 * Security app and other app blockers read through AppOpsManager. When that can't be read (not
 * Xiaomi, or a ROM that changed it), [isApplicable] is false and nothing is gated on it.
 *
 * Like Accessibility, it gates starting a session (manual/NFC from Home) and a scheduled start
 * (ScheduleReceiver skips it and notifies).
 */
object XiaomiAutostartUtil {
    private const val OP_AUTO_START = 10008

    fun isApplicable(context: Context): Boolean = readMode(context) != null

    /** True when Autostart is on, or when this isn't a Xiaomi device / the state can't be read. */
    fun isGranted(context: Context): Boolean = readMode(context)?.let { it == AppOpsManager.MODE_ALLOWED } ?: true

    private fun readMode(context: Context): Int? {
        if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return null
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return null
        return runCatching {
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            )
            method.invoke(appOps, OP_AUTO_START, Process.myUid(), context.packageName) as Int
        }.getOrNull()
    }

    /** Opens MIUI's Autostart list, falling back to Ctrus's App info screen (which has the toggle). */
    fun request(context: Context) {
        val candidates = listOf(
            Intent().setComponent(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ),
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
