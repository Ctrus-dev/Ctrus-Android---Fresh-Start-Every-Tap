package mo.dev.ctrus.util

import android.content.Context
import android.os.SystemClock

/**
 * Port of iOS's TrustedClock: measures real time passing without trusting the phone's date,
 * which anyone can move forward in Settings. Each instance keeps its own last reading under its
 * own [key], so separate features (the emergency and recovery cycles) don't eat into each
 * other's elapsed time.
 *
 * The uptime is [SystemClock.elapsedRealtime]: it keeps counting while the phone sleeps, ignores
 * the date, and only resets on a reboot (iOS uses CLOCK_MONOTONIC_RAW for the same reason).
 */
class TrustedClock(
    key: String,
    private val store: Store,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val uptimeMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    interface Store {
        fun getLong(key: String): Long?
        fun putLongs(values: Map<String, Long>)
    }

    private val lastWallClockKey = "trustedClock.$key.lastWallClock"
    private val lastUptimeKey = "trustedClock.$key.lastUptime"

    /** Real time since the previous call, in millis. Whenever it can't be sure, it counts less, never more. */
    fun trustedElapsedMillis(): Long = synchronized(lock) {
        val now = wallClockMillis()
        val currentUptime = uptimeMillis()
        val lastUptime = store.getLong(lastUptimeKey)
        val lastWallClock = store.getLong(lastWallClockKey)
        store.putLongs(mapOf(lastWallClockKey to now, lastUptimeKey to currentUptime))

        if (lastUptime == null || lastWallClock == null) return 0

        // Same boot: the uptime difference is the real time. Any extra the date moved on top of
        // it is a manual date change and is left out.
        if (currentUptime >= lastUptime) return currentUptime - lastUptime

        // Rebooted: the uptime only covers time since boot. The date is the only hint at how long
        // the phone was off, so it's trusted up to a cap.
        val poweredOff = (now - lastWallClock - currentUptime).coerceIn(0, MAX_POWERED_OFF_MILLIS)
        return (currentUptime + poweredOff).coerceAtLeast(0)
    }

    /** Backs [Store] with the app's own SharedPreferences — read synchronously on every check. */
    class SharedPreferencesStore(context: Context) : Store {
        private val prefs = context.getSharedPreferences("mo.dev.ctrus.trusted_clock", Context.MODE_PRIVATE)

        override fun getLong(key: String): Long? = if (prefs.contains(key)) prefs.getLong(key, 0) else null

        override fun putLongs(values: Map<String, Long>) {
            prefs.edit().apply { values.forEach { (k, v) -> putLong(k, v) } }.commit()
        }
    }

    companion object {
        const val MAX_POWERED_OFF_MILLIS = 24L * 60 * 60 * 1000

        // One lock for every instance, so two instances made for the same key can't both read the
        // same last reading and count the same time twice.
        private val lock = Any()
    }
}
