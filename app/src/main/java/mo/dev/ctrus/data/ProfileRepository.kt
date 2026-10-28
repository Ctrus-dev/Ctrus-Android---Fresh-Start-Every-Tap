package mo.dev.ctrus.data

import kotlinx.coroutines.flow.Flow
import mo.dev.ctrus.strategy.StrategyIds
import java.time.Instant

/** Mirrors the static methods on BlockedProfiles.swift, backed by Room instead of SwiftData. */
class ProfileRepository(private val dao: BlockedProfileDao) {
    fun observeAll(): Flow<List<BlockedProfileEntity>> = dao.observeAll()

    fun observe(id: String): Flow<BlockedProfileEntity?> = dao.observeById(id)

    suspend fun getAll(): List<BlockedProfileEntity> = dao.getAll()

    suspend fun find(id: String): BlockedProfileEntity? = dao.getById(id)

    suspend fun mostRecentlyUpdated(): BlockedProfileEntity? = dao.getMostRecentlyUpdated()

    suspend fun create(
        name: String,
        selectedPackages: List<String> = emptyList(),
        blockingStrategyId: String = StrategyIds.NFC,
        strategyData: String? = null,
        domains: List<String>? = null,
        physicalUnblockItems: List<PhysicalUnblockItem>? = null,
        enableBreaks: Boolean = false,
        breakTimeInMinutes: Int = 10,
        allowMultipleBreaks: Boolean = false,
        enableStrictMode: Boolean = false,
        enableBlockAppInstallation: Boolean = false,
        enableAllowMode: Boolean = false,
        enableAllowModeDomains: Boolean = false,
        enableBrowserBlocking: Boolean = true,
        enableAdultContentBlocking: Boolean = false,
        disableBackgroundStops: Boolean = false,
        enableEmergencyUnblock: Boolean = true,
        schedule: ProfileSchedule? = null,
        allowSkip: Boolean = false,
    ): BlockedProfileEntity {
        val nextOrder = (dao.getMaxOrder() ?: -1) + 1
        val profile = BlockedProfileEntity(
            name = name,
            selectedPackages = selectedPackages,
            blockingStrategyId = blockingStrategyId,
            strategyData = strategyData,
            order = nextOrder,
            enableBreaks = enableBreaks,
            breakTimeInMinutes = breakTimeInMinutes,
            allowMultipleBreaks = allowMultipleBreaks,
            enableStrictMode = enableStrictMode,
            enableBlockAppInstallation = enableBlockAppInstallation,
            enableAllowMode = enableAllowMode,
            enableAllowModeDomains = enableAllowModeDomains,
            enableBrowserBlocking = enableBrowserBlocking,
            enableAdultContentBlocking = enableAdultContentBlocking,
            domains = domains,
            physicalUnblockItems = PhysicalUnblockItem.normalizedItems(physicalUnblockItems),
            disableBackgroundStops = disableBackgroundStops,
            enableEmergencyUnblock = enableEmergencyUnblock,
            schedule = schedule,
            allowSkip = allowSkip,
        )
        dao.upsert(profile)
        return profile
    }

    suspend fun update(profile: BlockedProfileEntity): BlockedProfileEntity {
        val touched = profile.copy(
            updatedAtEpochMilli = Instant.now().toEpochMilli(),
            physicalUnblockItems = PhysicalUnblockItem.normalizedItems(profile.physicalUnblockItems),
        )
        dao.update(touched)
        return touched
    }

    suspend fun delete(profile: BlockedProfileEntity) {
        dao.delete(profile)
    }

    suspend fun clone(source: BlockedProfileEntity, newName: String): BlockedProfileEntity {
        val nextOrder = (dao.getMaxOrder() ?: -1) + 1
        val cloned = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = newName,
            order = nextOrder,
            createdAtEpochMilli = Instant.now().toEpochMilli(),
            updatedAtEpochMilli = Instant.now().toEpochMilli(),
            // allowSkip carries over, the skips themselves don't (iOS's clone does the same).
            skipNextOccurrence = null,
            skipUntil = null,
        )
        dao.upsert(cloned)
        return cloned
    }

    /** Mirrors BlockedProfiles.updateScheduleSkip. Leaves `updatedAt` alone, like iOS. */
    suspend fun updateScheduleSkip(profile: BlockedProfileEntity, skip: ScheduleSkip): BlockedProfileEntity {
        val updated = profile.copy(
            skipNextOccurrence = skip.skipNextOccurrence?.toEpochMilli(),
            skipUntil = skip.skipUntil?.toEpochMilli(),
        )
        dao.update(updated)
        return updated
    }

    /** Mirrors BlockedProfiles.clearExpiredScheduleSkips — run on app open and return. */
    suspend fun clearExpiredScheduleSkips() {
        dao.getAll().forEach { profile ->
            val skip = ScheduleSkip(
                skipNextOccurrence = profile.skipNextOccurrence?.let(Instant::ofEpochMilli),
                skipUntil = profile.skipUntil?.let(Instant::ofEpochMilli),
            )
            val active = skip.clearingExpired()
            if (active != skip) updateScheduleSkip(profile, active)
        }
    }

    suspend fun reorder(profiles: List<BlockedProfileEntity>) {
        profiles.forEachIndexed { index, profile ->
            if (profile.order != index) dao.update(profile.copy(order = index))
        }
    }

    suspend fun addDomain(profile: BlockedProfileEntity, domain: String): BlockedProfileEntity {
        val existing = profile.domains ?: return profile
        if (domain in existing) return profile
        return update(profile.copy(domains = existing + domain))
    }

    suspend fun removeDomain(profile: BlockedProfileEntity, domain: String): BlockedProfileEntity {
        val existing = profile.domains ?: return profile
        return update(profile.copy(domains = existing.filterNot { it == domain }))
    }
}
