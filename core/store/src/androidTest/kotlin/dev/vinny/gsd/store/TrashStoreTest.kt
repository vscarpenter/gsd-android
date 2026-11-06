package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A [Clock] whose instant can be advanced mid-test, to prove restore does not re-stamp time. */
private class MutableClock(
    private var current: Instant,
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)
    override fun instant(): Instant = current
    fun advanceBy(millis: Long) {
        current = current.plusMillis(millis)
    }
}

@RunWith(AndroidJUnit4::class)
class TrashStoreTest {
    private val clock = MutableClock(Instant.parse("2026-07-19T12:00:00.000Z"))
    private lateinit var db: GsdDatabase
    private lateinit var taskStore: TaskStore
    private lateinit var trashStore: TrashStore

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        taskStore = TaskStore(db, clock)
        trashStore = TrashStore(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(id: String, title: String = "t") =
        Task(id = id, title = title, urgent = false, important = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private suspend fun seedTask(id: String, title: String = "t"): Task = taskStore.add(draft(id, title))

    /** Inserts directly into the trash table, bypassing TaskStore.delete, for purge fixtures. */
    private suspend fun seedTrashRow(id: String, deletedAt: Instant) {
        val entity = draft(id).toEntity()
        db.deletedTaskDao().upsert(
            DeletedTaskEntity(
                id = entity.id,
                title = entity.title,
                description = entity.description,
                urgent = entity.urgent,
                important = entity.important,
                quadrant = entity.quadrant,
                completed = entity.completed,
                completedAt = entity.completedAt,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                dueDate = entity.dueDate,
                recurrence = entity.recurrence,
                tagsJson = entity.tagsJson,
                subtasksJson = entity.subtasksJson,
                dependenciesJson = entity.dependenciesJson,
                parentTaskId = entity.parentTaskId,
                notifyBefore = entity.notifyBefore,
                notificationEnabled = entity.notificationEnabled,
                notificationSent = entity.notificationSent,
                lastNotificationAt = entity.lastNotificationAt,
                snoozedUntil = entity.snoozedUntil,
                estimatedMinutes = entity.estimatedMinutes,
                timeEntriesJson = entity.timeEntriesJson,
                deletedAt = deletedAt.toEpochMilli(),
            ),
        )
    }

    @Test
    fun deleteMovesToTrashAndEnqueuesTombstoneFirst() = runTest {
        val task = seedTask("trash-task-01")
        taskStore.delete(task.id)
        assertNull(db.taskDao().byId(task.id))
        assertEquals(task.id, db.deletedTaskDao().byId(task.id)?.id)
        // seedTask's own CREATE is still pending too — the tombstone is the latest row for this id.
        val queued = db.syncQueueDao().pending().last { it.taskId == task.id }
        assertEquals(SyncOp.DELETE, queued.operation)
    }

    @Test
    fun restoreReactivatesPreservingTimestampsAndEnqueuesCreate() = runTest {
        val original = seedTask("trash-task-02")
        taskStore.delete(original.id)
        // prove restore does NOT stamp updatedAt (P§3.1: faithful undo, matches web+iOS)
        clock.advanceBy(1_000)
        val restored = trashStore.restore(original.id)!!
        assertNull(db.deletedTaskDao().byId(original.id))
        assertEquals(original.id, db.taskDao().byId(original.id)?.id)
        assertEquals(original.updatedAt, restored.updatedAt)
        assertEquals(SyncOp.CREATE, db.syncQueueDao().pending().last { it.taskId == original.id }.operation)
    }

    @Test
    fun purgeDropsStrictlyOlderThanStartOfDayCutoffAndKeepsOnCutoff() = runTest {
        val zone = ZoneId.systemDefault()
        val now = clock.instant()
        val cutoff = now.atZone(zone).toLocalDate().minusDays(30).atStartOfDay(zone).toInstant()
        seedTrashRow("on-cutoff-task", deletedAt = cutoff)
        seedTrashRow("older-task-0001", deletedAt = cutoff.minusMillis(1))
        seedTrashRow("fresh-task-0001", deletedAt = now)

        assertEquals(1, trashStore.purgeExpired())
        assertEquals(setOf("on-cutoff-task", "fresh-task-0001"), db.deletedTaskDao().allIds().toSet())
    }

    @Test
    fun abandonToTrashMovesTaskToTrashAndDropsItsQueueRowsEnqueueingNothing() = runTest {
        val task = seedTask("trash-task-05")
        // seedTask's own CREATE is pending; add a second pending row for the same task.
        db.syncQueueDao().insert(SyncQueueEntity.tombstone(task.id, clock.instant()))
        assertEquals(2, db.syncQueueDao().countForTask(task.id))

        // The verdict's precondition (spec 9.4): every row for the task has failed. A pending row protects it.
        db.syncQueueDao().pending().filter { it.taskId == task.id }.forEach { row ->
            db.syncQueueDao().bumpRetry(row.id, 5, "boom", 4_000L, SyncQueueEntity.STATUS_FAILED, 4_000L)
        }

        db.abandonToTrash(task.id, 5_000L)

        assertEquals(5_000L, db.deletedTaskDao().byId(task.id)?.deletedAt)
        assertNull(db.taskDao().byId(task.id))
        assertEquals(0, db.syncQueueDao().countForTask(task.id))
        assertTrue(db.syncQueueDao().pending().isEmpty()) // nothing enqueued
    }

    @Test
    fun deleteForeverAndEmptyEnqueueNothing() = runTest {
        val t1 = seedTask("trash-task-03")
        val t2 = seedTask("trash-task-04")
        taskStore.delete(t1.id)
        taskStore.delete(t2.id)
        val queuedBefore = db.syncQueueDao().pending().size

        trashStore.deleteForever(t1.id)
        trashStore.empty()

        assertEquals(queuedBefore, db.syncQueueDao().pending().size)
        assertTrue(db.deletedTaskDao().allIds().isEmpty())
    }
}
