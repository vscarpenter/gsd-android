package dev.vinny.gsd.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(entry: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY id")
    suspend fun pending(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE taskId = :taskId AND status IN ('pending', 'failed')")
    suspend fun countForTask(taskId: String): Int

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)
}
