package dev.vinny.gsd.store

import androidx.room.withTransaction
import dev.vinny.gsd.model.RecurrenceEngine
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskDuplicator
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
 *
 * Two kinds of write (spec §7.11). [add], [update], [edit], and [replaceAll] carry what the user typed, so
 * they validate and can throw. Everything else is a state change to a task that is already stored,
 * so it only normalizes and never rejects. A synced task can break a limit here, because iOS counts
 * characters and Android counts UTF-16 units, and it must still complete, move, and snooze.
 */
class TaskStore(private val db: GsdDatabase, private val clock: Clock) {

    fun observeTasks(): Flow<List<Task>> =
        db.taskDao().observeAll().map { rows -> rows.map { it.toTask() } }

    suspend fun get(id: String): Task? = db.taskDao().byId(id)?.toTask()

    suspend fun add(task: Task): Task {
        val now = clock.instant()
        val stamped = TaskValidator.validate(task, now).copy(createdAt = now, updatedAt = now)
        write(SyncOp.CREATE, stamped, now)
        return stamped
    }

    suspend fun update(task: Task): Task {
        val now = clock.instant()
        val stamped = TaskValidator.validate(task, now).copy(updatedAt = now)
        write(SyncOp.UPDATE, stamped, now)
        return stamped
    }

    /**
     * A save from the editor. It is typed input, so it validates. The row is read inside the
     * transaction, so the transform sees the task as it is now and not as it was when the sheet
     * opened. Null when the task is gone, and then nothing is written.
     */
    suspend fun edit(id: String, transform: (Task) -> Task): Task? {
        val now = clock.instant()
        return db.withTransaction {
            val current = db.taskDao().byId(id)?.toTask() ?: return@withTransaction null
            val edited = TaskValidator.validate(transform(current), now).copy(updatedAt = now)
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, edited, now))
            db.taskDao().upsert(edited.toEntity())
            edited
        }
    }

    /** A state change to a stored task: quadrant, due date, snooze, timers. Null when the task is gone. */
    suspend fun change(id: String, transform: (Task) -> Task): Task? {
        val current = get(id) ?: return null
        val now = clock.instant()
        val changed = TaskValidator.normalize(transform(current), now).copy(updatedAt = now)
        write(SyncOp.UPDATE, changed, now)
        return changed
    }

    /** A copy holds nothing the user typed just now, so it only normalizes. Null when the task is gone. */
    suspend fun duplicate(id: String): Task? {
        val original = get(id) ?: return null
        val now = clock.instant()
        val copy = TaskValidator.normalize(TaskDuplicator.duplicate(original, now), now)
        write(SyncOp.CREATE, copy, now)
        return copy
    }

    private suspend fun write(op: SyncOp, task: Task, now: Instant) {
        db.withTransaction {
            db.syncQueueDao().insert(SyncQueueEntity.forTask(op, task, now))
            db.taskDao().upsert(task.toEntity())
        }
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
     *
     * Completion is a transition. The row is read inside the transaction, which Room runs one at a
     * time, so a double-fired complete sees the first one's result. A call that asks for the state
     * the task already has writes nothing: no second instance, no restamp that would win
     * last-write-wins elsewhere, and no queue row.
     */
    suspend fun setCompleted(id: String, completed: Boolean): Task? {
        val now = clock.instant()
        return db.withTransaction {
            val current = db.taskDao().byId(id)?.toTask() ?: return@withTransaction null
            if (current.completed == completed) return@withTransaction current
            val updated = TaskValidator.normalize(
                current.copy(completed = completed, completedAt = if (completed) now else null),
                now,
            ).copy(updatedAt = now)
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, updated, now))
            db.taskDao().upsert(updated.toEntity())
            val spawned = if (completed) RecurrenceEngine.spawnNext(updated, now) else null
            if (spawned != null) {
                val nextInstance = TaskValidator.normalize(spawned, now)
                db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.CREATE, nextInstance, now))
                db.taskDao().upsert(nextInstance.toEntity())
            }
            updated
        }
    }

    suspend fun snooze(id: String, until: Instant?): Task? = change(id) { it.copy(snoozedUntil = until) }

    suspend fun startTimer(id: String): Task? = change(id) { TimeTracking.start(it, clock.instant()) }

    suspend fun stopTimer(id: String): Task? = change(id) { TimeTracking.stop(it, clock.instant()) }

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
