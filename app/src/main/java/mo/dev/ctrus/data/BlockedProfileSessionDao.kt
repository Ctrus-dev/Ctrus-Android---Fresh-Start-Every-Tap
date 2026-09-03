package mo.dev.ctrus.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedProfileSessionDao {
    @Query("SELECT * FROM blocked_profile_sessions WHERE endTimeEpochMilli IS NULL ORDER BY startTimeEpochMilli DESC LIMIT 1")
    suspend fun getMostRecentActive(): BlockedProfileSessionEntity?

    @Query("SELECT * FROM blocked_profile_sessions WHERE endTimeEpochMilli IS NULL ORDER BY startTimeEpochMilli DESC LIMIT 1")
    fun observeMostRecentActive(): Flow<BlockedProfileSessionEntity?>

    @Query("SELECT * FROM blocked_profile_sessions WHERE id = :id")
    suspend fun getById(id: String): BlockedProfileSessionEntity?

    @Query("SELECT * FROM blocked_profile_sessions WHERE profileId = :profileId ORDER BY startTimeEpochMilli DESC")
    fun observeForProfile(profileId: String): Flow<List<BlockedProfileSessionEntity>>

    @Query("SELECT * FROM blocked_profile_sessions WHERE endTimeEpochMilli IS NOT NULL ORDER BY endTimeEpochMilli DESC LIMIT :limit")
    suspend fun getRecentInactive(limit: Int = 50): List<BlockedProfileSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: BlockedProfileSessionEntity)

    @Update
    suspend fun update(session: BlockedProfileSessionEntity)
}
