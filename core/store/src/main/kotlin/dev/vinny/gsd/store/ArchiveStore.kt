package dev.vinny.gsd.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.withTransaction
import dev.vinny.gsd.model.Task
import java.time.Clock
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface ArchivedTaskDao {
    @Query("SELECT * FROM archived_tasks ORDER BY archivedAt DESC")
    fun observeAll(): Flow<List<ArchivedTaskEntity>>

    @Query("SELECT * FROM archived_tasks WHERE id = :id")
    suspend fun byId(id: String): ArchivedTaskEntity?

    @Upsert
    suspend fun upsert(entity: ArchivedTaskEntity)

    @Query("DELETE FROM archived_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM archived_tasks")
    suspend fun clear()
}

@Dao
interface ArchiveSettingsDao {
    @Query("SELECT * FROM archive_settings WHERE id = 1")
    fun observe(): Flow<ArchiveSettingsEntity?>

    @Query("SELECT * FROM archive_settings WHERE id = 1")
    suspend fun get(): ArchiveSettingsEntity?

    @Upsert
    suspend fun upsert(entity: ArchiveSettingsEntity)

    @Query("DELETE FROM archive_settings")
    suspend fun clear()
}

/**
 * Archive transitions sync as active-record transitions (spec §8.12):
 * archiving enqueues a remote DELETE; restoring enqueues a CREATE. Archived
 * rows themselves never sync.
 */
class ArchiveStore(private val db: GsdDatabase, private val clock: Clock) {
    class NotCompleted : Exception("Only completed tasks can be archived")

    fun observeArchived(): Flow<List<Task>> =
        db.archivedTaskDao().observeAll().map { rows -> rows.map { it.toTask() } }

    suspend fun archive(id: String) {
        val entity = db.taskDao().byId(id) ?: return
        if (!entity.completed) throw NotCompleted()
        val now = clock.instant()
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.tombstone(id, now))
            db.archivedTaskDao().upsert(entity.toArchived(archivedAt = now.toEpochMilli()))
            db.taskDao().deleteById(id)
        }
    }

    suspend fun restore(id: String) {
        val archived = db.archivedTaskDao().byId(id) ?: return
        val now = clock.instant()
        val task = archived.toTask().copy(updatedAt = now)
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.CREATE, task, now))
            db.taskDao().upsert(task.toEntity())
            db.archivedTaskDao().deleteById(id)
        }
    }

    suspend fun deletePermanently(id: String) {
        db.archivedTaskDao().deleteById(id)
    }

    /** Auto-archive sweep (spec §8.12): completed longer ago than the threshold. */
    suspend fun sweep(archiveAfterDays: Int): Int {
        val cutoff = clock.instant().minus(archiveAfterDays.toLong(), ChronoUnit.DAYS).toEpochMilli()
        val eligible = db.taskDao().all()
            .filter { it.completed && it.completedAt != null && it.completedAt < cutoff }
        eligible.forEach { archive(it.id) }
        return eligible.size
    }
}

private fun TaskEntity.toArchived(archivedAt: Long): ArchivedTaskEntity = ArchivedTaskEntity(
    id = id, title = title, description = description, urgent = urgent, important = important,
    quadrant = quadrant, completed = completed, completedAt = completedAt, createdAt = createdAt,
    updatedAt = updatedAt, dueDate = dueDate, recurrence = recurrence, tagsJson = tagsJson,
    subtasksJson = subtasksJson, dependenciesJson = dependenciesJson, parentTaskId = parentTaskId,
    notifyBefore = notifyBefore, notificationEnabled = notificationEnabled,
    notificationSent = notificationSent, lastNotificationAt = lastNotificationAt,
    snoozedUntil = snoozedUntil, estimatedMinutes = estimatedMinutes,
    timeEntriesJson = timeEntriesJson, archivedAt = archivedAt,
)

internal fun ArchivedTaskEntity.toTask(): Task = TaskEntity(
    id = id, title = title, description = description, urgent = urgent, important = important,
    quadrant = quadrant, completed = completed, completedAt = completedAt, createdAt = createdAt,
    updatedAt = updatedAt, dueDate = dueDate, recurrence = recurrence, tagsJson = tagsJson,
    subtasksJson = subtasksJson, dependenciesJson = dependenciesJson, parentTaskId = parentTaskId,
    notifyBefore = notifyBefore, notificationEnabled = notificationEnabled,
    notificationSent = notificationSent, lastNotificationAt = lastNotificationAt,
    snoozedUntil = snoozedUntil, estimatedMinutes = estimatedMinutes,
    timeEntriesJson = timeEntriesJson,
).toTask()
