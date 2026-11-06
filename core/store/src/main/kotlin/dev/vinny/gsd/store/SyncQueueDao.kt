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

    @Query("SELECT * FROM sync_queue WHERE status = 'failed' ORDER BY id")
    suspend fun failed(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'pending'")
    fun observePendingCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'failed'")
    fun observeFailedCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query(
        "UPDATE sync_queue SET retryCount = :retryCount, lastError = :error, " +
            "lastAttemptAt = :attemptAt, status = :status, failedAt = :failedAt WHERE id = :id",
    )
    suspend fun bumpRetry(id: Long, retryCount: Int, error: String, attemptAt: Long, status: String, failedAt: Long?)

    @Query("UPDATE sync_queue SET status = 'pending' WHERE status = 'failed'")
    suspend fun requeueFailed()

    @Query("SELECT COUNT(*) FROM sync_queue WHERE taskId = :taskId AND status IN ('pending', 'failed')")
    suspend fun countForTask(taskId: String): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE taskId = :taskId AND status = 'pending'")
    suspend fun countPendingForTask(taskId: String): Int

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)

    @Query("DELETE FROM sync_queue WHERE operation = :operation")
    suspend fun deleteByOperation(operation: SyncOp)

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}
