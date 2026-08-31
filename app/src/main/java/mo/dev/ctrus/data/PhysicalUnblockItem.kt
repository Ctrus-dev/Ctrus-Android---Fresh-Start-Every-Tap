package mo.dev.ctrus.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A named physical NFC tag that can unblock a profile. Mirrors PhysicalUnblockItem.swift.
 * Android only ever writes/reads NFC-type tags (see [mo.dev.ctrus.nfc.NfcCodec]), so unlike
 * iOS there's no other [PhysicalUnblockType] to model yet — kept as an enum anyway so a future
 * type doesn't require a schema change.
 */
@Serializable
enum class PhysicalUnblockType { NFC }

@Serializable
data class PhysicalUnblockItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: PhysicalUnblockType = PhysicalUnblockType.NFC,
    val codeValue: String,
) {
    companion object {
        /** Trims whitespace and drops empty entries, matching PhysicalUnblockItem.normalizedItems. */
        fun normalizedItems(items: List<PhysicalUnblockItem>?): List<PhysicalUnblockItem> =
            items.orEmpty()
                .map { it.copy(name = it.name.trim(), codeValue = it.codeValue.trim()) }
                .filter { it.codeValue.isNotEmpty() }

        fun normalizedCodeValue(value: String, type: PhysicalUnblockType): String =
            when (type) {
                PhysicalUnblockType.NFC -> value.trim().lowercase()
            }
    }
}

/** Mirrors BlockedProfiles.canUnblock(withCode:type:) on iOS. */
fun BlockedProfileEntity.canUnblockWithNfcCode(code: String): Boolean {
    val normalized = PhysicalUnblockItem.normalizedCodeValue(code, PhysicalUnblockType.NFC)
    return physicalUnblockItems.any {
        it.type == PhysicalUnblockType.NFC &&
            PhysicalUnblockItem.normalizedCodeValue(it.codeValue, it.type) == normalized
    }
}

fun BlockedProfileEntity.hasPhysicalUnblockItem(type: PhysicalUnblockType = PhysicalUnblockType.NFC): Boolean =
    physicalUnblockItems.any { it.type == type }
