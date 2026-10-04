package mo.dev.ctrus.permissions

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * "Alarms & reminders" (SCHEDULE_EXACT_ALARM). Scheduled starts, the 5-minute reminder, a fixed
 * Duration's auto-stop and break ends all need exact alarms to fire on time. Google Play only
 * allows USE_EXACT_ALARM (granted automatically) for alarm-clock and calendar apps, so Ctrus
 * relies on SCHEDULE_EXACT_ALARM instead. That one is granted at install on Android 12–13, but
 * **denied by default from Android 14**, so the user has to turn it on once in system settings.
 * Below Android 12 the permission doesn't exist and exact alarms always work.
 *
 * Like Accessibility, it gates starting a session (manual/NFC from Home) and a scheduled start
 * (ScheduleReceiver skips it and notifies).
 */
object ExactAlarmPermissionUtil {
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return true
        return alarmManager.canScheduleExactAlarms()
    }

    /** Opens the system's "Alarms & reminders" toggle for Ctrus. No-op below Android 12. */
    fun request(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
        )
    }
}
