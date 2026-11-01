package mo.dev.ctrus.util

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock

/**
 * The time a running session goes by: the phone's date when the session started, plus the real
 * time passed since ([SystemClock.elapsedRealtime], which a date change in Settings doesn't move).
 * Breaks and fixed-duration schedules are measured with it, so moving the date back no longer
 * stretches a break, and moving it forward no longer ends a schedule early (the user's call,
 * 2026-11-01). Re-anchored to the phone's date when a new session starts, and after a reboot,
 * where the uptime restarts (the date is trusted then, as [TrustedClock] does up to a cap).
 *
 * Only for comparing against times recorded during the session (break start/end, the session's
 * start); alarms are still armed in wall-clock time, converted with [toWallClock].
 */
object SessionClock {
    private const val KEY_SESSION = "sessionId"
    private const val KEY_WALL = "wall"
    private const val KEY_UPTIME = "uptime"
    private const val KEY_BOOT = "boot"

    private var prefs: SharedPreferences? = null
    private var bootCount: () -> Long? = { null }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("mo.dev.ctrus.session_clock", Context.MODE_PRIVATE)
        bootCount = TrustedClock.bootCount(context)
    }

    /** Called whenever the running session is (re)observed; a new session takes the phone's date as it is now. */
    @Synchronized
    fun ensureAnchored(sessionId: String) {
        val p = prefs ?: return
        if (p.getString(KEY_SESSION, null) == sessionId && isAnchorValid(p)) return
        anchor(p, sessionId)
    }

    @Synchronized
    fun now(): Long {
        val wall = System.currentTimeMillis()
        val p = prefs ?: return wall
        if (!p.contains(KEY_WALL)) return wall
        if (!isAnchorValid(p)) {
            anchor(p, p.getString(KEY_SESSION, null))
            return wall
        }
        return p.getLong(KEY_WALL, wall) + (SystemClock.elapsedRealtime() - p.getLong(KEY_UPTIME, 0))
    }

    /** The phone's date at which [sessionTime] (a time on this clock) comes round. */
    fun toWallClock(sessionTime: Long): Long = System.currentTimeMillis() + (sessionTime - now())

    private fun isAnchorValid(p: SharedPreferences): Boolean {
        if (SystemClock.elapsedRealtime() < p.getLong(KEY_UPTIME, Long.MAX_VALUE)) return false
        val boot = bootCount()
        return boot == null || !p.contains(KEY_BOOT) || p.getLong(KEY_BOOT, -1) == boot
    }

    private fun anchor(p: SharedPreferences, sessionId: String?) {
        p.edit().apply {
            putString(KEY_SESSION, sessionId)
            putLong(KEY_WALL, System.currentTimeMillis())
            putLong(KEY_UPTIME, SystemClock.elapsedRealtime())
            bootCount()?.let { putLong(KEY_BOOT, it) } ?: remove(KEY_BOOT)
        }.commit()
    }
}
