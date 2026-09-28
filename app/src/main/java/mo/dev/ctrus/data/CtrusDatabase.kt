package mo.dev.ctrus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BlockedProfileEntity::class, BlockedProfileSessionEntity::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class CtrusDatabase : RoomDatabase() {
    abstract fun blockedProfileDao(): BlockedProfileDao
    abstract fun sessionDao(): BlockedProfileSessionDao

    companion object {
        /** Adds the nullable JSON `schedule` column for "Schedule + Ctrus NFC" profiles. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocked_profiles ADD COLUMN schedule TEXT")
            }
        }

        @Volatile private var instance: CtrusDatabase? = null

        fun getInstance(context: Context): CtrusDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CtrusDatabase::class.java,
                    "ctrus.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
