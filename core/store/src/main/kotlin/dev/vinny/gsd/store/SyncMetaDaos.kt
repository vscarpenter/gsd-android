package dev.vinny.gsd.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetadataDao {
    @Query("SELECT * FROM sync_metadata WHERE id = 1")
    suspend fun get(): SyncMetadataEntity?

    @Upsert
    suspend fun upsert(entity: SyncMetadataEntity)
}

data class SyncHistoryTotals(
    val totalSyncs: Int = 0,
    val successes: Int = 0,
    val totalPushed: Int = 0,
    val totalPulled: Int = 0,
)

@Dao
interface SyncHistoryDao {
    @Insert
    suspend fun insert(entity: SyncHistoryEntity): Long

    @Query("SELECT * FROM sync_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<SyncHistoryEntity>>

    @Query("SELECT COUNT(*) FROM sync_history")
    suspend fun count(): Int

    /** Over the retained rows, so the pruning limit (spec §9.7) caps them, as on web and iOS. */
    @Query(
        "SELECT COUNT(*) AS totalSyncs, COALESCE(SUM(status = 'success'), 0) AS successes, " +
            "COALESCE(SUM(pushedCount), 0) AS totalPushed, COALESCE(SUM(pulledCount), 0) AS totalPulled " +
            "FROM sync_history",
    )
    fun observeTotals(): Flow<SyncHistoryTotals>

    /** Keep only the 500 most recent (spec §9.7). */
    @Query(
        "DELETE FROM sync_history WHERE id NOT IN " +
            "(SELECT id FROM sync_history ORDER BY timestamp DESC LIMIT 500)",
    )
    suspend fun prune()
}
