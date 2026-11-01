package mo.dev.ctrus.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.childmode.ChildTag
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
    val breakTimeInMinutes: Int = 5,
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

    /** Only set for "Schedule + Ctrus NFC" and "Alarm + Ctrus NFC" profiles — see [ProfileSchedule]. */
    val schedule: ProfileSchedule? = null,

    /** Lets the profile's ⋯ menu skip schedule sessions — see [ScheduleSkip]. */
    val allowSkip: Boolean = false,
    /** Epoch millis of the one skipped occurrence's start. */
    val skipNextOccurrence: Long? = null,
    /** Epoch millis of the start of the last skipped day. */
    val skipUntil: Long? = null,

    /** Alarm mode: shows the Snooze button on the alarm screen and the card. */
    val enableSnooze: Boolean = true,
    /** Alarm mode's "Ring only once": the profile is deleted once its session ends. */
    val isOneTimeAlarm: Boolean = false,

    /** Child Mode only: the optional child's Ctrus. */
    val childTag: ChildTag? = null,
    /** Child Mode only: whether [childTag] can end sessions too. Always false without one. */
    val childTagCanStop: Boolean = false,
) {
    /** Ignored unless [allowSkip] is on, like iOS's snapshot. */
    val scheduleSkip: ScheduleSkip
        get() = if (!allowSkip) ScheduleSkip() else ScheduleSkip(
            skipNextOccurrence = skipNextOccurrence?.let(Instant::ofEpochMilli),
            skipUntil = skipUntil?.let(Instant::ofEpochMilli),
        )

    /**
     * Skipping needs one of the profile's own tags to authorize it, so a profile without one never
     * offers it. In Child Mode the parents authorize it instead, so no tag is needed there.
     */
    val canSkipSessions: Boolean
        get() = allowSkip && blockingStrategyId in StrategyIds.SCHEDULED_IDS && schedule?.isActive == true &&
            (ChildModeStore.isChildMode || physicalUnblockItems.any { it.type == PhysicalUnblockType.NFC })

    /**
     * Child Mode: only a parents' Ctrus, or this profile's child's Ctrus when it's allowed to, can
     * end a session — matched by UID, never by URL.
     */
    fun childModeAllowsEnding(uid: String): Boolean {
        if (ChildModeStore.isParentTag(uid)) return true
        val childTag = childTag ?: return false
        return childTagCanStop && ChildModeStore.normalizedUid(childTag.uid) == ChildModeStore.normalizedUid(uid)
    }
}
