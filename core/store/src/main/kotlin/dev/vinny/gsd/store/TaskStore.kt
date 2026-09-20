package dev.vinny.gsd.store

import androidx.room.withTransaction
import dev.vinny.gsd.model.RecurrenceEngine
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.model.TaskValidator
import dev.vinny.gsd.model.TimeTracking
import java.time.Clock
import java.time.Instant
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

    /**
     * Records that a reminder posted. This is device-local bookkeeping (spec §4.7), so it is the one
     * local write that neither restamps `updatedAt` nor enqueues a push. Going through [update] let a
     * fired reminder win last-write-wins over a newer edit made on another device.
     */
    suspend fun markReminderSent(id: String, at: Instant) =
        db.taskDao().markReminderSent(id, at.toEpochMilli())

    /**
     * Completing a recurring task also spawns its next instance — in the same
     * transaction, so the instance and its CREATE queue row land atomically
     * with the completion (spec §8.5).
     */
    suspend fun setCompleted(id: String, completed: Boolean): Task? {
        val current = get(id) ?: return null
        val now = clock.instant()
        val updated = TaskValidator.validate(
            current.copy(completed = completed, completedAt = if (completed) now else null),
            now,
        ).copy(updatedAt = now)
        val spawned = if (completed) RecurrenceEngine.spawnNext(updated, now) else null
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, updated, now))
            db.taskDao().upsert(updated.toEntity())
            if (spawned != null) {
                val validSpawn = TaskValidator.validate(spawned, now)
                db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.CREATE, validSpawn, now))
                db.taskDao().upsert(validSpawn.toEntity())
            }
        }
        return updated
    }

    suspend fun snooze(id: String, until: Instant?): Task? {
        val current = get(id) ?: return null
        return update(current.copy(snoozedUntil = until))
    }

    suspend fun startTimer(id: String): Task? {
        val current = get(id) ?: return null
        return update(TimeTracking.start(current, clock.instant()))
    }

    suspend fun stopTimer(id: String): Task? {
        val current = get(id) ?: return null
        return update(TimeTracking.stop(current, clock.instant()))
    }

    /**
     * Replace-mode import (spec §8.16): the imported set becomes the store —
     * UPDATE enqueued per imported task, DELETE per existing id missing from
     * the import, table swapped in one transaction.
     */
    suspend fun replaceAll(imported: List<Task>) {
        val now = clock.instant()
        val stamped = imported.map { TaskValidator.validate(it, now).copy(updatedAt = now) }
        val importedIds = stamped.map { it.id }.toSet()
        db.withTransaction {
            db.taskDao().all().filter { it.id !in importedIds }.forEach { row ->
                db.syncQueueDao().insert(SyncQueueEntity.tombstone(row.id, now))
                db.taskDao().deleteById(row.id)
            }
            stamped.forEach { task ->
                db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, task, now))
                db.taskDao().upsert(task.toEntity())
            }
        }
    }

    /**
     * Removes the task's id from every other task's dependencies first (spec §8.8). The
     * in-transaction row read is null-safe: a benign race (e.g. a same-id SSE delete, or a
     * double-fired swipe re-entering this transaction) can make the row vanish between the
     * initial [get] and the transaction — in that case the already-enqueued tombstone is
     * harmless and the trash move is simply skipped.
     */
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
            db.taskDao().byId(id)?.let { db.deletedTaskDao().upsert(it.toDeleted(deletedAt = now.toEpochMilli())) }
            db.taskDao().deleteById(id)
        }
        return snapshot
    }
}
