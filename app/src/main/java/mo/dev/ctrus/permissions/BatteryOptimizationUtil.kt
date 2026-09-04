package mo.dev.ctrus.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Whether the OS is still allowed to apply Doze/App Standby battery restrictions to this app.
 * Several OEM skins (MIUI, ColorOS, FuntouchOS/OriginOS, and OxygenOS since it moved onto the
 * ColorOS base) kill background services — including [mo.dev.ctrus.blocking.BlockingAccessibilityService]
 * — far more aggressively than stock Android's Doze mode, silently ending blocking mid-session.
 * Being exempted from battery optimization is the one lever the OS exposes uniformly across
 * OEMs to reduce (not eliminate — some skins have their own battery managers on top) that risk.
 */
object BatteryOptimizationUtil {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Launches the system's own "exempt this app" confirmation dialog. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    }
}
