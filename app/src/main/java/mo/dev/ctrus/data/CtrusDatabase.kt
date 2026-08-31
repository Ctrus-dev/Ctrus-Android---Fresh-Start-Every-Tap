package mo.dev.ctrus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [BlockedProfileEntity::class, BlockedProfileSessionEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class CtrusDatabase : RoomDatabase() {
    abstract fun blockedProfileDao(): BlockedProfileDao
    abstract fun sessionDao(): BlockedProfileSessionDao

    companion object {
        @Volatile private var instance: CtrusDatabase? = null

        fun getInstance(context: Context): CtrusDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CtrusDatabase::class.java,
                    "ctrus.db",
                ).build().also { instance = it }
            }
    }
}
