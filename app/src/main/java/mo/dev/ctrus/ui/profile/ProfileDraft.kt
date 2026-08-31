package mo.dev.ctrus.ui.profile

import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.PhysicalUnblockItem

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
)

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
)
