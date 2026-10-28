package mo.dev.ctrus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import mo.dev.ctrus.strategy.StrategyIds
import java.time.Instant
import java.util.UUID

/**
 * Room equivalent of Ctrus/Models/BlockedProfiles.swift. `selectedPackages` replaces iOS's
 * opaque `FamilyActivitySelection` (no Android equivalent) with plain installed-app package
 * names — see [mo.dev.ctrus.picker.InstalledAppsRepository]. `enableSafariBlocking`/`domains`
 * are renamed `enableBrowserBlocking` since there's no Safari on Android; same meaning
 * (block matching domains when they're opened in the device's browser).
 */
@Entity(tableName = "blocked_profiles")
data class BlockedProfileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val selectedPackages: List<String> = emptyList(),
    val createdAtEpochMilli: Long = Instant.now().toEpochMilli(),
    val updatedAtEpochMilli: Long = Instant.now().toEpochMilli(),
    val blockingStrategyId: String,
    val strategyData: String? = null,
    val order: Int = 0,

    val showOngoingNotification: Boolean = false,
    val reminderTimeInSeconds: Long? = null,
    val customReminderMessage: String? = null,

    val enableBreaks: Boolean = false,
    val breakTimeInMinutes: Int = 10,
    val allowMultipleBreaks: Boolean = false,

    val enableStrictMode: Boolean = false,
    val enableBlockAppInstallation: Boolean = false,

    val enableAllowMode: Boolean = false,
    val enableAllowModeDomains: Boolean = false,
    val enableBrowserBlocking: Boolean = true,
    val enableAdultContentBlocking: Boolean = false,

    val physicalUnblockItems: List<PhysicalUnblockItem> = emptyList(),
    val domains: List<String>? = null,

    val disableBackgroundStops: Boolean = false,
    val enableEmergencyUnblock: Boolean = true,

    /** Only set for "Schedule + Ctrus NFC" profiles — see [ProfileSchedule]. */
    val schedule: ProfileSchedule? = null,

    /** Lets the profile's ⋯ menu skip schedule sessions — see [ScheduleSkip]. */
    val allowSkip: Boolean = false,
    /** Epoch millis of the one skipped occurrence's start. */
    val skipNextOccurrence: Long? = null,
    /** Epoch millis of the start of the last skipped day. */
    val skipUntil: Long? = null,
) {
    /** Ignored unless [allowSkip] is on, like iOS's snapshot. */
    val scheduleSkip: ScheduleSkip
        get() = if (!allowSkip) ScheduleSkip() else ScheduleSkip(
            skipNextOccurrence = skipNextOccurrence?.let(Instant::ofEpochMilli),
            skipUntil = skipUntil?.let(Instant::ofEpochMilli),
        )

    /** Skipping needs one of the profile's own tags to authorize it, so a profile without one never offers it. */
    val canSkipSessions: Boolean
        get() = allowSkip && blockingStrategyId == StrategyIds.SCHEDULE && schedule?.isActive == true &&
            physicalUnblockItems.any { it.type == PhysicalUnblockType.NFC }
}
