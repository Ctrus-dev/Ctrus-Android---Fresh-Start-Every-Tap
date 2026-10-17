package mo.dev.ctrus.blocking

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.MainActivity
import mo.dev.ctrus.R
import mo.dev.ctrus.permissions.AccessibilityPermissionUtil

/**
 * While a session is active, wakes Ctrus about every 15 minutes to check the Accessibility service
 * is still running, and posts one "Blocking stopped" notification per session if it isn't (an OEM
 * battery manager killed it, or it was turned off). Nothing is shown while things work, and no
 * ongoing notification is used — the user ruled that out. The check uses the same
 * enabled-services list as Home's banner, which leaves out a service Android marked as crashed
 * ("This service is malfunctioning").
 *
 * Limitation: a brand that stops Ctrus from waking up (e.g. MIUI with Autostart off) also stops
 * this check — see XiaomiAutostartUtil / OemBackgroundGuide.
 */
object BlockingHealthMonitor {
    private const val CHECK_INTERVAL_MILLIS = 15 * 60 * 1000L
    // A process woken by the alarm can briefly read as "not running" before the system rebinds
    // it. Kept under the 10 s a goAsync() receiver may run on the foreground broadcast queue.
    private const val RECHECK_DELAY_MILLIS = 8_000L
    private const val PREFS = "blocking_health"
    private const val KEY_WARNED_SESSION_ID = "warned_session_id"
    private const val NOTIFICATION_ID = 0x0C7B

    /** Call whenever the active session changes (null = none). */
    fun sync(context: Context, activeSessionId: String?) {
        if (activeSessionId == null) {
            cancel(context)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        // Keep an already-armed check instead of pushing it back every time the process starts.
        if (pendingIntent(context, PendingIntent.FLAG_NO_CREATE) == null) arm(context)
    }

    internal suspend fun check(context: Context) {
        val app = CtrusApp.from(context)
        val session = app.sessionRepository.mostRecentActive()
        if (session == null) {
            sync(context, null)
            return
        }
        arm(context)
        if (AccessibilityPermissionUtil.isEnabled(context)) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        delay(RECHECK_DELAY_MILLIS)
        if (AccessibilityPermissionUtil.isEnabled(context)) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_WARNED_SESSION_ID, null) == session.id) return
        val profileName = app.profileRepository.find(session.profileId)?.name ?: return
        if (notify(context, profileName)) prefs.edit { putString(KEY_WARNED_SESSION_ID, session.id) }
    }

    private fun notify(context: Context, profileName: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = context.getString(R.string.blocking_stopped_body, profileName)
        val notification = NotificationCompat.Builder(context, CtrusApp.BLOCKING_STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.blocking_stopped_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    // Inexact on purpose: a check every ~15 minutes doesn't need exact alarms, and
    // setAndAllowWhileIdle still runs in Doze (at most every ~9 minutes there).
    private fun arm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + CHECK_INTERVAL_MILLIS,
            operation,
        )
    }

    private fun cancel(context: Context) {
        val operation = pendingIntent(context, PendingIntent.FLAG_NO_CREATE) ?: return
        context.getSystemService(AlarmManager::class.java)?.cancel(operation)
        operation.cancel()
    }

    private fun pendingIntent(context: Context, flag: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, BlockingHealthReceiver::class.java),
        flag or PendingIntent.FLAG_IMMUTABLE,
    )
}

class BlockingHealthReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                BlockingHealthMonitor.check(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
