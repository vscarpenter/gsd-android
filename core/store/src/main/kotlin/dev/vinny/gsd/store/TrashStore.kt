package dev.vinny.gsd.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import dev.vinny.gsd.model.Task
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface DeletedTaskDao {
    @Query("SELECT * FROM deleted_tasks ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<DeletedTaskEntity>>

    @Query("SELECT * FROM deleted_tasks WHERE id = :id")
    suspend fun byId(id: String): DeletedTaskEntity?

    @Query("SELECT id FROM deleted_tasks")
    suspend fun allIds(): List<String>

    @Upsert
    suspend fun upsert(entity: DeletedTaskEntity)

    @Query("DELETE FROM deleted_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM deleted_tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM deleted_tasks WHERE deletedAt < :cutoffMs")
    suspend fun purgeOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM deleted_tasks")
    suspend fun clear()
}

/**
 * Deletion is recoverable for 30 days (parity spec P§3). The remote delete was
 * already enqueued when the task entered the trash, so restore is the only
 * operation here that syncs (as a fresh CREATE); delete-forever, empty, and
 * purge touch nothing remote.
 */
class TrashStore(private val db: GsdDatabase, private val clock: Clock) {
    fun observeTrash(): Flow<List<Pair<Task, Instant>>> =
        db.deletedTaskDao().observeAll().map { rows ->
            rows.map { it.toTask() to Instant.ofEpochMilli(it.deletedAt) }
        }

    suspend fun byId(id: String): Task? = db.deletedTaskDao().byId(id)?.toTask()

    suspend fun restore(id: String): Task? {
        val row = db.deletedTaskDao().byId(id) ?: return null
        val task = row.toTask() // timestamps preserved — faithful undo (P§3.1, matches web+iOS)
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.CREATE, task, clock.instant()))
            db.taskDao().upsert(task.toEntity())
            db.deletedTaskDao().deleteById(id)
        }
        return task
    }

    suspend fun deleteForever(id: String) = db.deletedTaskDao().deleteById(id)

    suspend fun empty() = db.deletedTaskDao().clear()

    suspend fun purgeExpired(): Int {
        val zone = ZoneId.systemDefault()
        val cutoff = clock.instant().atZone(zone).toLocalDate()
            .minusDays(30).atStartOfDay(zone).toInstant()
        return db.deletedTaskDao().purgeOlderThan(cutoff.toEpochMilli()) // < cutoff: on-cutoff kept
    }
}

/**
 * ABANDON verdict (S§8.3): a task whose only queue rows have exhausted retries
 * never reached the server — move it to trash instead of deleting it outright,
 * so it stays recoverable. Enqueues nothing; the delete never happened remotely.
 */
suspend fun GsdDatabase.abandonToTrash(id: String, deletedAtMs: Long) = withTransaction {
    val row = taskDao().byId(id) ?: return@withTransaction
    // The verdict came from an earlier queue read. A pending row now means the user edited the task
    // since, and the push will land it (sprint backlog RECONCILE-RECHECK).
    if (syncQueueDao().countPendingForTask(id) > 0) return@withTransaction
    deletedTaskDao().upsert(row.toDeleted(deletedAtMs))
    taskDao().deleteById(id)
    syncQueueDao().deleteForTask(id)
}

internal fun TaskEntity.toDeleted(deletedAt: Long): DeletedTaskEntity = DeletedTaskEntity(
    id = id, title = title, description = description, urgent = urgent, important = important,
    quadrant = quadrant, completed = completed, completedAt = completedAt, createdAt = createdAt,
    updatedAt = updatedAt, dueDate = dueDate, recurrence = recurrence, tagsJson = tagsJson,
    subtasksJson = subtasksJson, dependenciesJson = dependenciesJson, parentTaskId = parentTaskId,
    notifyBefore = notifyBefore, notificationEnabled = notificationEnabled,
    notificationSent = notificationSent, lastNotificationAt = lastNotificationAt,
    snoozedUntil = snoozedUntil, estimatedMinutes = estimatedMinutes,
    timeEntriesJson = timeEntriesJson, deletedAt = deletedAt, savedAt = savedAt,
)

internal fun DeletedTaskEntity.toTask(): Task = TaskEntity(
    id = id, title = title, description = description, urgent = urgent, important = important,
    quadrant = quadrant, completed = completed, completedAt = completedAt, createdAt = createdAt,
    updatedAt = updatedAt, dueDate = dueDate, recurrence = recurrence, tagsJson = tagsJson,
    subtasksJson = subtasksJson, dependenciesJson = dependenciesJson, parentTaskId = parentTaskId,
    notifyBefore = notifyBefore, notificationEnabled = notificationEnabled,
    notificationSent = notificationSent, lastNotificationAt = lastNotificationAt,
    snoozedUntil = snoozedUntil, estimatedMinutes = estimatedMinutes,
    timeEntriesJson = timeEntriesJson, savedAt = savedAt,
).toTask()
