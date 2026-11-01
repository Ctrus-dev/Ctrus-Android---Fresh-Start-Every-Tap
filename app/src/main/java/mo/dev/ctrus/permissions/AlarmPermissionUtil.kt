package mo.dev.ctrus.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * What Alarm mode needs before a profile can be saved (spec D3.6, Android): notifications (the
 * alarm is one), "Alarms & reminders" (SCHEDULE_EXACT_ALARM, never USE_EXACT_ALARM) for
 * `setAlarmClock`, on Android 14+ full-screen notifications, so it comes up over the lock screen,
 * and on Xiaomi MIUI's own "Show on Lock screen" ([XiaomiLockScreenUtil]), without which it doesn't.
 */
object AlarmPermissionUtil {
    enum class Missing { NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN, LOCK_SCREEN }

    fun firstMissing(context: Context): Missing? = when {
        !notificationsAllowed(context) -> Missing.NOTIFICATIONS
        !ExactAlarmPermissionUtil.isGranted(context) -> Missing.EXACT_ALARMS
        !fullScreenAllowed(context) -> Missing.FULL_SCREEN
        !XiaomiLockScreenUtil.isGranted(context) -> Missing.LOCK_SCREEN
        else -> null
    }

    fun notificationsAllowed(context: Context): Boolean {
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun fullScreenAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Ctrus's notification settings, for when the system won't ask again. */
    fun openNotificationSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openFullScreenSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
