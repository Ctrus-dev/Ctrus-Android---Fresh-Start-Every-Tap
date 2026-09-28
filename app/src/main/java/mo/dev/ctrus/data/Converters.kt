package mo.dev.ctrus.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

private val json = Json { ignoreUnknownKeys = true }

/** Room can only store primitive columns; these mirror the JSON-blob storage pattern iOS uses for [Shared.SharedData]. */
class Converters {
    @TypeConverter
    fun stringListToJson(value: List<String>?): String? =
        value?.let { json.encodeToString(ListSerializer(serializer<String>()), it) }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String>? =
        value?.let { runCatching { json.decodeFromString(ListSerializer(serializer<String>()), it) }.getOrNull() }

    @TypeConverter
    fun physicalUnblockItemsToJson(value: List<PhysicalUnblockItem>?): String? =
        value?.let { json.encodeToString(ListSerializer(PhysicalUnblockItem.serializer()), it) }

    @TypeConverter
    fun jsonToPhysicalUnblockItems(value: String?): List<PhysicalUnblockItem>? =
        value?.let {
            runCatching { json.decodeFromString(ListSerializer(PhysicalUnblockItem.serializer()), it) }.getOrNull()
        }

    @TypeConverter
    fun scheduleToJson(value: ProfileSchedule?): String? =
        value?.let { json.encodeToString(ProfileSchedule.serializer(), it) }

    @TypeConverter
    fun jsonToSchedule(value: String?): ProfileSchedule? =
        value?.let { runCatching { json.decodeFromString(ProfileSchedule.serializer(), it) }.getOrNull() }
}
