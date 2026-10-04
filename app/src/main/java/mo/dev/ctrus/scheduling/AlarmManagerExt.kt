package mo.dev.ctrus.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

/**
 * Exact while-idle alarm when "Alarms & reminders" is granted, otherwise the inexact while-idle
 * variant. Calling setExactAndAllowWhileIdle without the permission throws a SecurityException
 * on Android 12+, which used to crash starting a break on Android 14+ (where the permission is
 * off by default). An inexact alarm can arrive late but never early, and everything that relies on
 * these alarms also checks the clock itself (e.g. a break counts as over once its time is up).
 */
fun AlarmManager.setWhileIdleBestEffort(triggerAtEpochMilli: Long, operation: PendingIntent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
        setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMilli, operation)
    } else {
        setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMilli, operation)
    }
}
