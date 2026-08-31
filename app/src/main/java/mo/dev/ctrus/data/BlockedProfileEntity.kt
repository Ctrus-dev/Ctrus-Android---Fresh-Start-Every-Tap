package mo.dev.ctrus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
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
)
