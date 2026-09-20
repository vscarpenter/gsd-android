package dev.vinny.gsd.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: String): TaskEntity?

    @Upsert
    suspend fun upsert(entity: TaskEntity)

    /** Device-local reminder state only (spec §4.7): `updatedAt`, the LWW key, stays untouched. */
    @Query("UPDATE tasks SET notificationSent = 1, lastNotificationAt = :atMs WHERE id = :id")
    suspend fun markReminderSent(id: String, atMs: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tasks")
    suspend fun clear()
}
