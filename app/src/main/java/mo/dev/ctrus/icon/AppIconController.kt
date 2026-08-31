package mo.dev.ctrus.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Android has no direct API to swap the launcher icon the way iOS's
 * `UIApplication.setAlternateIconName` does — this enables exactly one of the `<activity-alias>`
 * entries in AndroidManifest.xml and disables the rest. `DONT_KILL_APP` avoids restarting the
 * process (the alias switch alone is enough); most launchers only refresh the visible icon after
 * the next home-screen redraw or app restart, which is an Android platform limitation, not a bug
 * here.
 */
object AppIconController {
    fun select(context: Context, icon: AppIcon) {
        val packageManager = context.packageManager
        for (candidate in AppIcon.entries) {
            val state = if (candidate == icon) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            packageManager.setComponentEnabledSetting(
                ComponentName(context.packageName, candidate.aliasClassName),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
