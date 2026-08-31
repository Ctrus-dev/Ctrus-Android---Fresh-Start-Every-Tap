package mo.dev.ctrus.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedProfileDao {
    @Query("SELECT * FROM blocked_profiles ORDER BY `order` ASC, createdAtEpochMilli DESC")
    fun observeAll(): Flow<List<BlockedProfileEntity>>

    @Query("SELECT * FROM blocked_profiles ORDER BY `order` ASC, createdAtEpochMilli DESC")
    suspend fun getAll(): List<BlockedProfileEntity>

    @Query("SELECT * FROM blocked_profiles WHERE id = :id")
    suspend fun getById(id: String): BlockedProfileEntity?

    @Query("SELECT * FROM blocked_profiles WHERE id = :id")
    fun observeById(id: String): Flow<BlockedProfileEntity?>

    @Query("SELECT * FROM blocked_profiles ORDER BY updatedAtEpochMilli DESC LIMIT 1")
    suspend fun getMostRecentlyUpdated(): BlockedProfileEntity?

    @Query("SELECT MAX(`order`) FROM blocked_profiles")
    suspend fun getMaxOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: BlockedProfileEntity)

    @Update
    suspend fun update(profile: BlockedProfileEntity)

    @Delete
    suspend fun delete(profile: BlockedProfileEntity)
}
