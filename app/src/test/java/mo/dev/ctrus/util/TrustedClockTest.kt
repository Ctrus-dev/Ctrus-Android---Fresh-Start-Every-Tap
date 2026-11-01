package mo.dev.ctrus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    private var bootSession: Long? = 1L

    private val day = 24L * 60 * 60 * 1000

    private fun makeClock(key: String = "test") = TrustedClock(key, store, { wallClock }, { uptime }, { bootSession })

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

    @Test
    fun dateMovedForwardIsAClockJump() {
        val clock = makeClock()
        clock.read()
        wallClock += day
        uptime += 60_000
        val reading = clock.read()
        assertEquals(60_000, reading.elapsedMillis)
        assertTrue(reading.clockJumped)
    }

    @Test
    fun ordinaryTimeIsNotAClockJump() {
        val clock = makeClock()
        clock.read()
        wallClock += 3_600_000 + 1_000
        uptime += 3_600_000
        assertFalse(clock.read().clockJumped)
    }

    @Test
    fun rebootWithDateMovedBackIsAClockJump() {
        val clock = makeClock()
        clock.read()
        wallClock -= day
        uptime = 300_000
        assertTrue(clock.read().clockJumped)
    }

    // D12.3: a reboot is told by the boot count too, and a long gap across one isn't a date change.

    @Test
    fun rebootDaysAfterLastReadingIsNotAClockJump() {
        val clock = makeClock()
        clock.read()
        wallClock += 3 * day
        uptime = 600_000
        bootSession = 2
        assertFalse(clock.read().clockJumped)
    }

    @Test
    fun rebootWithLongerUptimeIsTreatedAsAReboot() {
        val clock = makeClock()
        clock.read()
        wallClock += 3 * day
        uptime = 20_000_000
        bootSession = 2
        val reading = clock.read()
        assertFalse(reading.clockJumped)
        assertEquals(20_000_000 + day, reading.elapsedMillis)
    }

    @Test
    fun noRebootDateMovedForwardADayIsAClockJump() {
        val clock = makeClock()
        clock.read()
        wallClock += day
        uptime += 60_000
        val reading = clock.read()
        assertTrue(reading.clockJumped)
        assertEquals(day - 60_000, reading.dateShiftMillis)
    }

    @Test
    fun rebootWithDateMovedBackReportsANegativeShift() {
        val clock = makeClock()
        clock.read()
        wallClock -= day
        uptime = 300_000
        bootSession = 2
        assertTrue(clock.read().dateShiftMillis < 0)
    }
}
