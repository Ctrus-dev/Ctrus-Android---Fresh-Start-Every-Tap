package mo.dev.ctrus.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import mo.dev.ctrus.R

/**
 * Per-brand steps for keeping Ctrus alive in the background. Several brands run their own
 * battery manager on top of Android's (which the battery-optimization exemption doesn't cover)
 * and close Ctrus, which stops the Accessibility service and with it all blocking. Only Xiaomi's
 * Autostart can be read (see XiaomiAutostartUtil); everything here is a recommendation that can't
 * be verified, so it never gates anything. Same approach as Switchly's background-access FAQ,
 * steps adapted from dontkillmyapp.com. Menu names vary between OS versions. Asus and
 * Tecno/Infinix/itel have their own entries (their screen names are untested on real phones, so
 * they may fall through to App info), and every other brand gets GENERIC, except Google's Pixel.
 */
enum class OemBackgroundGuide(@StringRes val stepsRes: Int, private val targets: List<ComponentName>) {
    XIAOMI(
        R.string.oem_steps_xiaomi,
        listOf(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")),
    ),
    HUAWEI(
        R.string.oem_steps_huawei,
        listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ),
    ),
    OPPO(
        R.string.oem_steps_oppo,
        listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ),
    ),
    VIVO(
        R.string.oem_steps_vivo,
        listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ),
    ),
    // Samsung's per-app Battery setting lives on the App info screen (the fallback below).
    SAMSUNG(R.string.oem_steps_samsung, emptyList()),
    ASUS(
        R.string.oem_steps_asus,
        listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.powersaver.PowerSaverSettings"),
        ),
    ),
    TRANSSION(
        R.string.oem_steps_transsion,
        listOf(ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")),
    ),
    // Any other brand: no screen of its own, so straight to App info.
    GENERIC(R.string.oem_steps_generic, emptyList());

    /** Tries the brand's own screen, then Ctrus's App info screen. */
    fun open(context: Context) {
        val intents = targets.map { component ->
            Intent().setComponent(component)
                .putExtra("package_name", context.packageName)
                .putExtra("package_label", context.getString(R.string.app_name))
        } + Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        for (intent in intents) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
    }

    companion object {
        /** The guide for this phone's brand: [GENERIC] if it isn't a known one, null only on Google's Pixel. */
        fun forDevice(): OemBackgroundGuide? = forBrand(Build.MANUFACTURER, Build.BRAND)

        internal fun forBrand(manufacturer: String, brand: String): OemBackgroundGuide? {
            val id = "$manufacturer $brand".lowercase()
            return when {
                listOf("xiaomi", "redmi", "poco").any(id::contains) -> XIAOMI
                listOf("huawei", "honor").any(id::contains) -> HUAWEI
                listOf("oppo", "realme", "oneplus").any(id::contains) -> OPPO
                listOf("vivo", "iqoo").any(id::contains) -> VIVO
                id.contains("samsung") -> SAMSUNG
                id.contains("asus") -> ASUS
                listOf("tecno", "infinix", "itel").any(id::contains) -> TRANSSION
                manufacturer.equals("Google", ignoreCase = true) -> null
                else -> GENERIC
            }
        }
    }
}
