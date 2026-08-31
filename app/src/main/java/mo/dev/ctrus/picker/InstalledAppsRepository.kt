package mo.dev.ctrus.picker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * Android replacement for iOS's FamilyActivityPicker (Ctrus/Components/BlockedProfileView/AppPicker.swift):
 * there's no system app-selection sheet with opaque tokens on Android, so this lists real
 * launchable installed apps via PackageManager and the profile stores plain package names.
 */
class InstalledAppsRepository(private val context: Context) {
    suspend fun listLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ownPackage = context.packageName

        pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != ownPackage }
            .mapNotNull { packageName ->
                runCatching {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    InstalledApp(
                        packageName = packageName,
                        label = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo),
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
