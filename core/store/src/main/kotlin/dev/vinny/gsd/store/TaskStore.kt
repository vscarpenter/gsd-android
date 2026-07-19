package dev.vinny.gsd.store

import androidx.room.withTransaction
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.model.TaskValidator
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The single mutation path for tasks (spec §3). Stamps updatedAt from the
 * injected clock and enqueues the sync operation before the row becomes
 * visible — in one transaction (spec §9.4's enqueue-before-write invariant).
 */
class TaskStore(private val db: GsdDatabase, private val clock: Clock) {

    fun observeTasks(): Flow<List<Task>> =
        db.taskDao().observeAll().map { rows -> rows.map { it.toTask() } }

    suspend fun get(id: String): Task? = db.taskDao().byId(id)?.toTask()

    suspend fun add(task: Task): Task {
        val now = clock.instant()
        val stamped = TaskValidator.validate(task, now).copy(createdAt = now, updatedAt = now)
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.CREATE, stamped, now))
            db.taskDao().upsert(stamped.toEntity())
        }
        return stamped
    }

    suspend fun update(task: Task): Task {
        val now = clock.instant()
        val stamped = TaskValidator.validate(task, now).copy(updatedAt = now)
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, stamped, now))
            db.taskDao().upsert(stamped.toEntity())
        }
        return stamped
    }

    suspend fun setCompleted(id: String, completed: Boolean): Task? {
        val current = get(id) ?: return null
        return update(
            current.copy(
                completed = completed,
                completedAt = if (completed) clock.instant() else null,
            ),
        )
    }

    /** Removes the task's id from every other task's dependencies first (spec §8.8). */
    suspend fun delete(id: String): Task? {
        val snapshot = get(id) ?: return null
        val now = clock.instant()
        db.withTransaction {
            db.taskDao().all()
                .filter { id in TaskJson.decodeIds(it.dependenciesJson) }
                .forEach { row ->
                    val trimmed = TaskJson.decodeIds(row.dependenciesJson) - id
                    val updated = row.copy(
                        dependenciesJson = TaskJson.encodeIds(trimmed),
                        updatedAt = now.toEpochMilli(),
                    )
                    db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, updated.toTask(), now))
                    db.taskDao().upsert(updated)
                }
            db.syncQueueDao().insert(SyncQueueEntity.tombstone(id, now))
            db.taskDao().deleteById(id)
        }
        return snapshot
    }
}
