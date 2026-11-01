package mo.dev.ctrus.childmode

import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.data.ProfileSchedule
import mo.dev.ctrus.strategy.StrategyIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class ChildModeTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val hour = 3_600_000L

    // Monday, 2 Nov 2026
    private fun date(day: Int, hour: Int = 12, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    private fun scheduleProfile(durationInHours: Int?, skipNext: ZonedDateTime? = null) = BlockedProfileEntity(
        name = "Night",
        blockingStrategyId = StrategyIds.SCHEDULE,
        schedule = ProfileSchedule(days = DayOfWeek.entries.map { it.value }, startHour = 22, startMinute = 0, durationInHours = durationInHours),
        allowSkip = skipNext != null,
        skipNextOccurrence = skipNext?.toInstant()?.toEpochMilli(),
    )

    @Test
    fun weakCodesAreRefused() {
        listOf("000000", "123456", "234567", "654321", "987654", "12345", "12a456").forEach {
            assertTrue(it, ParentCredentials.isWeakCode(it))
        }
        listOf("135790", "112233", "908172").forEach { assertFalse(it, ParentCredentials.isWeakCode(it)) }
    }

    @Test
    fun recoveryKeyIsNormalizedAndFormatted() {
        assertEquals("ABCD2345WXYZ", ParentCredentials.normalizedRecoveryKey("abcd-2345-wxyz"))
        assertEquals("ABCD-2345-WXYZ", ParentCredentials.formattedRecoveryKey("ABCD2345WXYZ"))
    }

    @Test
    fun sessionWithoutScheduleEndsAfter24Hours() {
        val profile = BlockedProfileEntity(name = "Manual", blockingStrategyId = StrategyIds.NFC_MANUAL)
        val plan = ChildSessionLimit.plan(profile, date(2, 9).toInstant(), zone)
        assertEquals(ChildSessionLimit.Plan(24 * hour, makesWayForNextSession = false), plan)
    }

    @Test
    fun fixedDurationEndsAtItsDuration() {
        val plan = ChildSessionLimit.plan(scheduleProfile(durationInHours = 2), date(2, 22).toInstant(), zone)
        assertEquals(ChildSessionLimit.Plan(2 * hour, makesWayForNextSession = false), plan)
    }

    @Test
    fun indefiniteDailyScheduleMakesWayFiveMinutesBeforeTheNextSession() {
        val plan = ChildSessionLimit.plan(scheduleProfile(durationInHours = null), date(2, 22).toInstant(), zone)
        assertEquals(ChildSessionLimit.Plan(24 * hour - 5 * 60_000, makesWayForNextSession = true), plan)
    }

    @Test
    fun skippedNextSessionDoesNotEndItEarly() {
        val plan = ChildSessionLimit.plan(scheduleProfile(durationInHours = null, skipNext = date(3, 22)), date(2, 22).toInstant(), zone)
        assertEquals(ChildSessionLimit.Plan(24 * hour, makesWayForNextSession = false), plan)
    }

    @Test
    fun leavingChildModeKeepsEveryLinkedCtrusOnce() {
        val profile = BlockedProfileEntity(
            name = "Night",
            blockingStrategyId = StrategyIds.NFC,
            physicalUnblockItems = listOf(PhysicalUnblockItem(name = "Ctrus 1", codeValue = "https://ctrus.pt/a")),
            childTag = ChildTag(uid = "04aabbcc", url = null),
        )
        val parents = listOf(
            ParentTag(uid = "0411", url = "https://ctrus.pt/a", label = "Parents' Ctrus 1"),
            ParentTag(uid = "0422", url = null, label = "Parents' Ctrus 2"),
        )
        val items = ChildModeSwitcher.physicalUnblocks(profile, parents) { "Ctrus $it" }
        assertEquals(listOf("https://ctrus.pt/a", "0422", "04AABBCC"), items.map { it.codeValue })
        assertEquals(listOf("Ctrus 1", "Ctrus 2", "Ctrus 3"), items.map { it.name })
    }

    @Test
    fun childTagOnlyEndsWhenAllowed() {
        val profile = BlockedProfileEntity(name = "P", blockingStrategyId = StrategyIds.NFC, childTag = ChildTag(uid = "04AA"))
        assertFalse(profile.childModeAllowsEnding("04aa"))
        assertTrue(profile.copy(childTagCanStop = true).childModeAllowsEnding("04:AA".replace(":", "")))
        assertFalse(profile.copy(childTagCanStop = true).childModeAllowsEnding("04bb"))
    }
}
