package mo.dev.ctrus.childmode

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.provider.Settings
import mo.dev.ctrus.CtrusApp

/**
 * Child Mode's `appStopped` entry (spec Part B, section 10, [Android]): the Accessibility service
 * came back after having stopped during a session.
 *
 * Checked each time the service connects, against the boot it last connected in
 * (`Settings.Global.BOOT_COUNT`):
 * - same boot: it was force-stopped and came back without a reboot — logged, unless the app was
 *   just updated (and, from Android 11, not when the system killed it — see [wasStoppedByUser]);
 * - the very next boot: an ordinary reboot — not logged (agreed with the user);
 * - a boot was skipped: Ctrus didn't run in one of them, which is what safe mode looks like —
 *   logged.
 * Only while a session is running, and only when the app's process started over: the system also
 * unbinds and rebinds the service in the same process without anything having stopped (e.g. while
 * a UiAutomation client is connected); turning Accessibility off is its own `permissionRevoked`.
 */
object ServiceStopWatch {
    private const val KEY_BOOT_COUNT = "serviceLastBootCount"
    private const val KEY_CONNECTED_AT = "serviceLastConnectedAt"

    /** The service already connected once in this process. */
    @Volatile private var connectedInThisProcess = false

    suspend fun onServiceConnected(app: CtrusApp) {
        val isRebind = connectedInThisProcess
        connectedInThisProcess = true
        val prefs = app.getSharedPreferences("mo.dev.ctrus.service_watch", Context.MODE_PRIVATE)
        val bootCount = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val lastBootCount = prefs.getInt(KEY_BOOT_COUNT, -1)
        val lastConnectedAt = prefs.getLong(KEY_CONNECTED_AT, 0)
        prefs.edit().putInt(KEY_BOOT_COUNT, bootCount).putLong(KEY_CONNECTED_AT, System.currentTimeMillis()).commit()

        if (isRebind || !ChildModeStore.isChildMode || bootCount < 0 || lastBootCount < 0) return
        val session = app.sessionRepository.mostRecentActive() ?: return

        val stopped = when {
            bootCount == lastBootCount -> {
                val updatedAt = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime }.getOrDefault(0)
                session.startTimeEpochMilli < lastConnectedAt && updatedAt < lastConnectedAt && wasStoppedByUser(app)
            }
            bootCount > lastBootCount + 1 -> true
            else -> false
        }
        if (stopped) ChildModeStore.logTamper(TamperLogEntry.Kind.APP_STOPPED)
    }

    /**
     * The entry says Ctrus was stopped in the phone's settings, so from Android 11 (where the
     * reason the last process ended is known) only a force stop counts — not the system killing
     * it for memory or battery, which MIUI does when the screen locks. Before 11 it can't tell.
     */
    private fun wasStoppedByUser(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        val reason = context.getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(context.packageName, 0, 1)
            .firstOrNull()?.reason ?: return true
        return reason == ApplicationExitInfo.REASON_USER_REQUESTED || reason == ApplicationExitInfo.REASON_USER_STOPPED
    }
}
