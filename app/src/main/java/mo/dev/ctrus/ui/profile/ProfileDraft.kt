package mo.dev.ctrus.ui.profile

import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.data.ProfileSchedule
import mo.dev.ctrus.strategy.StrategyIds
import java.time.DayOfWeek

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
    val breakTimeInMinutes: Int = 10,
    val allowMultipleBreaks: Boolean = false,
    val enableStrictMode: Boolean = true,
    val enableBlockAppInstallation: Boolean = false,
    val scheduleDays: Set<DayOfWeek> = emptySet(),
    val scheduleStartHour: Int = 9,
    val scheduleStartMinute: Int = 0,
    /** `null` = Indefinite (NFC-only stop). */
    val scheduleDurationHours: Int? = null,
) {
    /** Mirrors BlockedProfileDraft.useSchedule. */
    val useSchedule: Boolean get() = strategyId == StrategyIds.SCHEDULE

    /**
     * Mirrors BlockedProfileDraft.selectedStrategy's didSet: leaving Schedule for another mode
     * drops the chosen days, so no "ghost" schedule stays armed in the background.
     */
    fun withStrategy(id: String): ProfileDraft =
        if (id == StrategyIds.SCHEDULE) copy(strategyId = id) else copy(strategyId = id, scheduleDays = emptySet())

    val schedule: ProfileSchedule?
        get() = if (useSchedule && scheduleDays.isNotEmpty()) {
            ProfileSchedule(scheduleDays.map { it.value }.sorted(), scheduleStartHour, scheduleStartMinute, scheduleDurationHours)
        } else {
            null
        }
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
)
