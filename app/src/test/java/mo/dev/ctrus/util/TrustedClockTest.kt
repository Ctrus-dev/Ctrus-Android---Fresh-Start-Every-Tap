package mo.dev.ctrus.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Port of iOS's TrustedClockTests. */
class TrustedClockTest {
    private val store = object : TrustedClock.Store {
        val values = mutableMapOf<String, Long>()
        override fun getLong(key: String): Long? = values[key]
        override fun putLongs(values: Map<String, Long>) {
            this.values.putAll(values)
        }
    }
    private var wallClock = 1_000_000_000L
    private var uptime = 10_000_000L

    private val day = 24L * 60 * 60 * 1000

    private fun makeClock(key: String = "test") = TrustedClock(key, store, { wallClock }, { uptime })

    @Test
    fun firstReadingCountsNothing() {
        assertEquals(0, makeClock().trustedElapsedMillis())
    }

    @Test
    fun noRebootCountsUptime() {
        val clock = makeClock()
        clock.trustedElapsedMillis()
        wallClock += 3_600_000
        uptime += 3_600_000
        assertEquals(3_600_000, clock.trustedElapsedMillis())
    }

    @Test
    fun noRebootDateMovedForward30DaysOnlyRealTimeCounts() {
        val clock = makeClock()
        clock.trustedElapsedMillis()
        wallClock += 30 * day
        uptime += 60_000
        assertEquals(60_000, clock.trustedElapsedMillis())
    }

    @Test
    fun rebootDateMovedForward30DaysCountsAtMost24Hours() {
        val clock = makeClock()
        clock.trustedElapsedMillis()
        wallClock += 30 * day
        uptime = 120_000
        assertEquals(120_000 + day, clock.trustedElapsedMillis())
    }

    @Test
    fun rebootAfterBrieflyOffCountsTheTimeOff() {
        val clock = makeClock()
        clock.trustedElapsedMillis()
        wallClock += 3_600_000
        uptime = 600_000
        assertEquals(3_600_000, clock.trustedElapsedMillis())
    }

    @Test
    fun rebootWithDateMovedBackIsNeverNegative() {
        val clock = makeClock()
        clock.trustedElapsedMillis()
        wallClock -= 10 * day
        uptime = 300_000
        assertEquals(300_000, clock.trustedElapsedMillis())
    }

    @Test
    fun separateKeysDontShareReadings() {
        val first = makeClock()
        val second = makeClock("other")
        first.trustedElapsedMillis()
        second.trustedElapsedMillis()
        wallClock += 100_000
        uptime += 100_000
        first.trustedElapsedMillis()
        assertEquals(100_000, second.trustedElapsedMillis())
    }
}
