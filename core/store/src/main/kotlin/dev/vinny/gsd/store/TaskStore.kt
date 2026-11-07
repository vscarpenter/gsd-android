package dev.vinny.gsd.store

import androidx.room.withTransaction
import dev.vinny.gsd.model.RecurrenceEngine
import dev.vinny.gsd.model.ReminderOffset
import dev.vinny.gsd.model.SavedPlacement
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskDuplicator
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.model.TaskValidator
import dev.vinny.gsd.model.TimeTracking
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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

    /** This device's default reminder, for rules that compare effective offsets (D-10). */
    suspend fun defaultReminder(): Int = db.defaultReminderMinutes()

    suspend fun add(task: Task): Task {
        val now = clock.instant()
        val stamped = TaskValidator.validate(task, now).copy(createdAt = now, updatedAt = now).withOffset()
        write(SyncOp.CREATE, stamped, now)
        return stamped
    }

    suspend fun update(task: Task): Task {
        val now = clock.instant()
        val stamped = TaskValidator.validate(task, now).copy(updatedAt = now).withOffset()
        write(SyncOp.UPDATE, stamped, now)
        return stamped
    }

    /**
     * A save from the editor. What the user sees and typed, [typed], is input, so it validates. The
     * row is read inside the transaction, so [transform] sees the task as it is now and not as it
     * was when the sheet opened. That row only normalizes: a field the user never touched may have
     * arrived by sync over a limit (iOS counts characters, Android UTF-16 units), and it must not fail
     * the save. Null when the task is gone, and then nothing is written.
     */
    suspend fun edit(id: String, typed: Task, transform: (Task) -> Task): Task? {
        val now = clock.instant()
        TaskValidator.validate(typed, now)
        return db.withTransaction {
            val current = db.taskDao().byId(id)?.toTask() ?: return@withTransaction null
            val edited = TaskValidator.normalize(transform(current), now).copy(updatedAt = now).withOffset()
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, edited, now))
            db.taskDao().upsert(edited.toEntity())
            edited
        }
    }

    /**
     * A state change to a stored task: quadrant, due date, snooze, timers. Null when the task is gone.
     * The row is read inside the transaction, as in [edit], so a snooze or a timer write can never
     * overwrite an editor save that committed a moment earlier.
     */
    suspend fun change(id: String, refuseSaved: Boolean = false, transform: (Task) -> Task): Task? {
        val now = clock.instant()
        return db.withTransaction {
            val current = db.taskDao().byId(id)?.toTask() ?: return@withTransaction null
            // Saved work waits for a return to the matrix (web ADR 0018): nothing is written
            if (refuseSaved && current.isSaved) return@withTransaction current
            val changed = TaskValidator.normalize(transform(current), now).copy(updatedAt = now).withOffset()
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, changed, now))
            db.taskDao().upsert(changed.toEntity())
            changed
        }
    }

    /** A copy holds nothing the user typed just now, so it only normalizes. Null when the task is gone. */
    suspend fun duplicate(id: String): Task? {
        val original = get(id) ?: return null
        val now = clock.instant()
        val copy = TaskValidator.normalize(TaskDuplicator.duplicate(original, now), now).withOffset()
        write(SyncOp.CREATE, copy, now)
        return copy
    }

    /**
     * D-10: every write stores a number, so a task's reminder means the same on every device. A legacy null
     * resolves to this device's default, which it was already firing, the next time anything writes its row.
     */
    private suspend fun Task.withOffset(): Task = ReminderOffset.resolve(this, db.defaultReminderMinutes())

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
            if (completed && current.isSaved) return@withTransaction current // completes only after a return
            val updated = TaskValidator.normalize(
                current.copy(completed = completed, completedAt = if (completed) now else null),
                now,
            ).copy(updatedAt = now).withOffset()
            db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.UPDATE, updated, now))
            db.taskDao().upsert(updated.toEntity())
            val spawned = if (completed) RecurrenceEngine.spawnNext(updated, now, ZoneId.systemDefault()) else null
            if (spawned != null) {
                val nextInstance = TaskValidator.normalize(spawned, now)
                db.syncQueueDao().insert(SyncQueueEntity.forTask(SyncOp.CREATE, nextInstance, now))
                db.taskDao().upsert(nextInstance.toEntity())
            }
            updated
        }
    }

    suspend fun snooze(id: String, until: Instant?): Task? =
        change(id, refuseSaved = true) { it.copy(snoozedUntil = until) }

    suspend fun startTimer(id: String): Task? =
        change(id, refuseSaved = true) { TimeTracking.start(it, clock.instant()) }

    /** Throws [SavedPlacement.CannotSave] for a completed task or one with a running timer. */
    suspend fun moveToSaved(id: String): Task? = change(id) { SavedPlacement.save(it, clock.instant()) }

    /** Back to active work with the quadrant and date the user reviewed; resets reminder state. */
    suspend fun returnToMatrix(id: String, urgent: Boolean, important: Boolean, dueDate: Instant?): Task? =
        change(id) { SavedPlacement.returnToMatrix(it, urgent, important, dueDate) }

    /** Notes are the description. What the user typed validates; the rest of the row is read fresh. */
    suspend fun editNotes(id: String, notes: String): Task? {
        val current = get(id) ?: return null
        return edit(id, current.copy(description = notes)) { it.copy(description = notes) }
    }

    suspend fun stopTimer(id: String): Task? = change(id) { TimeTracking.stop(it, clock.instant()) }

    /**
     * Replace-mode import (spec §8.16): the imported set becomes the store —
     * UPDATE enqueued per imported task, DELETE per existing id missing from
     * the import, table swapped in one transaction.
     */
    suspend fun replaceAll(imported: List<Task>) {
        val now = clock.instant()
        val stamped = imported.map { TaskValidator.validate(it, now).copy(updatedAt = now).withOffset() }
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
