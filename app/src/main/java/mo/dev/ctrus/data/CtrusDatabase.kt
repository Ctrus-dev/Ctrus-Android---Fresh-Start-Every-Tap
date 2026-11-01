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
    version = 5,
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

        /** Adds the schedule-skip fields (`allowSkip`, `skipNextOccurrence`, `skipUntil`). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocked_profiles ADD COLUMN allowSkip INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_profiles ADD COLUMN skipNextOccurrence INTEGER")
                db.execSQL("ALTER TABLE blocked_profiles ADD COLUMN skipUntil INTEGER")
            }
        }

        /** Adds Child Mode's per-profile fields (`childTag`, `childTagCanStop`). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocked_profiles ADD COLUMN childTag TEXT")
                db.execSQL("ALTER TABLE blocked_profiles ADD COLUMN childTagCanStop INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds Alarm mode's per-profile fields (`enableSnooze`, `isOneTimeAlarm`). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE blocked_profiles ADD COLUMN enableSnooze INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE blocked_profiles ADD COLUMN isOneTimeAlarm INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile private var instance: CtrusDatabase? = null

        fun getInstance(context: Context): CtrusDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CtrusDatabase::class.java,
                    "ctrus.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
