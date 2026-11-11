package mo.dev.ctrus.ui.profile

import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.childmode.ChildTag
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.data.ProfileSchedule
import mo.dev.ctrus.strategy.StrategyIds
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/** All the editable fields, mirroring BlockedProfileDraft.swift. Shared by [ProfileFormScreen] (edit, all sections at once) and [GuidedProfileCreationScreen] (create, one section per step). */
data class ProfileDraft(
    val name: String = "",
    val strategyId: String,
    val selectedPackages: Set<String> = emptySet(),
    val enableAllowMode: Boolean = false,
    val enableBrowserBlocking: Boolean = true,
    val domains: List<String> = emptyList(),
    val enableAllowModeDomains: Boolean = false,
    val enableAdultContentBlocking: Boolean = false,
    val physicalUnblockItems: List<PhysicalUnblockItem> = emptyList(),
    val enableBreaks: Boolean = false,
    val breakTimeInMinutes: Int = 5,
    val allowMultipleBreaks: Boolean = false,
    val enableStrictMode: Boolean = true,
    val enableBlockAppInstallation: Boolean = false,
    val scheduleDays: Set<DayOfWeek> = emptySet(),
    val scheduleStartHour: Int = 9,
    val scheduleStartMinute: Int = 0,
    /** `null` = Indefinite (NFC-only stop). */
    val scheduleDurationHours: Int? = null,
    val allowSkip: Boolean = false,
    /** Alarm mode only (spec D2): the end time, which is when the alarm rings. */
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val alarmSound: String = ProfileSchedule.DEFAULT_ALARM_SOUND,
    val snoozeMinutes: Int = ProfileSchedule.DEFAULT_SNOOZE_MINUTES,
    val enableSnooze: Boolean = true,
    val snoozeLimit: Int = ProfileSchedule.DEFAULT_SNOOZE_LIMIT,
    val isOneTimeAlarm: Boolean = false,
    /** Child Mode only (spec Part B, 6.1). */
    val childTag: ChildTag? = null,
    val childTagCanStop: Boolean = false,
) {
    /** Child Mode's master key is the parents' Ctrus, so a profile doesn't need tags of its own there. */
    val hasRequiredUnlocks: Boolean get() = ChildModeStore.isChildMode || physicalUnblockItems.isNotEmpty()

    /** Mirrors BlockedProfileDraft.useSchedule: Schedule and Alarm both start on their own and share the schedule step. */
    val useSchedule: Boolean get() = strategyId in StrategyIds.SCHEDULED_IDS

    val useAlarm: Boolean get() = strategyId == StrategyIds.ALARM

    val useOneTimeAlarm: Boolean get() = useAlarm && isOneTimeAlarm

    /** Mirrors BlockedProfileDraft.hasValidSchedule: the schedule step can be left / the profile saved. */
    val hasValidSchedule: Boolean
        get() = (useOneTimeAlarm || scheduleDays.isNotEmpty()) && (!useAlarm || hasValidAlarmTime)

    val hasValidAlarmTime: Boolean get() = alarmHour != scheduleStartHour || alarmMinute != scheduleStartMinute

    /**
     * Mirrors BlockedProfileDraft.selectedStrategy's didSet: leaving Schedule/Alarm for another
     * mode drops the chosen days, so no "ghost" schedule stays armed in the background; and Alarm
     * mode never ends on a timer — the alarm does that.
     */
    fun withStrategy(id: String): ProfileDraft = when (id) {
        StrategyIds.ALARM -> copy(strategyId = id, scheduleDurationHours = null)
        StrategyIds.SCHEDULE -> copy(strategyId = id)
        else -> copy(strategyId = id, scheduleDays = emptySet())
    }

    /**
     * What's actually saved. A one-time alarm runs on whichever day its start time comes round
     * next, counted from a minute from now, and has no later sessions to skip (spec D3.4).
     */
    fun forSaving(now: ZonedDateTime = ZonedDateTime.now()): ProfileDraft {
        if (!useOneTimeAlarm) return this
        val from = now.plusSeconds(60)
        val sameDay = from.with(LocalTime.of(scheduleStartHour, scheduleStartMinute))
        val start = if (sameDay.isAfter(from)) sameDay else sameDay.plusDays(1)
        return copy(scheduleDays = setOf(start.dayOfWeek), allowSkip = false)
    }

    val schedule: ProfileSchedule?
        get() = if (useSchedule && scheduleDays.isNotEmpty()) {
            ProfileSchedule(
                days = scheduleDays.map { it.value }.sorted(),
                startHour = scheduleStartHour,
                startMinute = scheduleStartMinute,
                durationInHours = if (useAlarm) null else scheduleDurationHours,
                alarmHour = alarmHour.takeIf { useAlarm },
                alarmMinute = alarmMinute.takeIf { useAlarm },
                alarmSound = alarmSound.takeIf { useAlarm },
                snoozeMinutes = snoozeMinutes.takeIf { useAlarm },
                snoozeLimit = snoozeLimit.takeIf { useAlarm },
            )
        } else {
            null
        }

    /** The schedule as it stands in the form, days or not — for the alarm line and the summary. */
    val draftSchedule: ProfileSchedule
        get() = ProfileSchedule(
            days = scheduleDays.map { it.value }.sorted(),
            startHour = scheduleStartHour,
            startMinute = scheduleStartMinute,
            alarmHour = alarmHour,
            alarmMinute = alarmMinute,
        )
}

fun BlockedProfileEntity.toDraft() = ProfileDraft(
    name = name,
    strategyId = blockingStrategyId,
    selectedPackages = selectedPackages.toSet(),
    enableAllowMode = enableAllowMode,
    enableBrowserBlocking = enableBrowserBlocking,
    domains = domains.orEmpty(),
    enableAllowModeDomains = enableAllowModeDomains,
    enableAdultContentBlocking = enableAdultContentBlocking,
    physicalUnblockItems = physicalUnblockItems,
    enableBreaks = enableBreaks,
    breakTimeInMinutes = breakTimeInMinutes,
    allowMultipleBreaks = allowMultipleBreaks,
    enableStrictMode = enableStrictMode,
    enableBlockAppInstallation = enableBlockAppInstallation,
    scheduleDays = schedule?.daysOfWeek.orEmpty(),
    scheduleStartHour = schedule?.startHour ?: 9,
    scheduleStartMinute = schedule?.startMinute ?: 0,
    scheduleDurationHours = schedule?.durationInHours,
    allowSkip = allowSkip,
    alarmHour = schedule?.alarmTime?.hour ?: 7,
    alarmMinute = schedule?.alarmTime?.minute ?: 0,
    alarmSound = schedule?.alarmSoundId ?: ProfileSchedule.DEFAULT_ALARM_SOUND,
    snoozeMinutes = schedule?.alarmSnoozeMinutes ?: ProfileSchedule.DEFAULT_SNOOZE_MINUTES,
    enableSnooze = enableSnooze,
    snoozeLimit = schedule?.alarmSnoozeLimit ?: ProfileSchedule.DEFAULT_SNOOZE_LIMIT,
    isOneTimeAlarm = isOneTimeAlarm,
    childTag = childTag,
    childTagCanStop = childTagCanStop,
)

/** Writes every draft field onto [profile] (edit), or onto a fresh entity when creating. */
fun ProfileDraft.applyTo(profile: BlockedProfileEntity): BlockedProfileEntity = profile.copy(
    name = name.trim(),
    selectedPackages = selectedPackages.toList(),
    blockingStrategyId = strategyId,
    domains = domains,
    enableAllowMode = enableAllowMode,
    enableBrowserBlocking = enableBrowserBlocking,
    enableAllowModeDomains = enableAllowModeDomains,
    enableAdultContentBlocking = enableAdultContentBlocking,
    physicalUnblockItems = physicalUnblockItems,
    enableBreaks = enableBreaks,
    breakTimeInMinutes = breakTimeInMinutes,
    allowMultipleBreaks = allowMultipleBreaks,
    enableStrictMode = enableStrictMode,
    enableBlockAppInstallation = enableBlockAppInstallation,
    schedule = schedule,
    allowSkip = allowSkip,
    // Turning skipping off drops any skip already set, like iOS's BlockedProfiles.updateProfile.
    skipNextOccurrence = profile.skipNextOccurrence.takeIf { allowSkip },
    skipUntil = profile.skipUntil.takeIf { allowSkip },
    enableSnooze = enableSnooze,
    isOneTimeAlarm = useOneTimeAlarm,
    childTag = childTag,
    childTagCanStop = childTag != null && childTagCanStop,
)
