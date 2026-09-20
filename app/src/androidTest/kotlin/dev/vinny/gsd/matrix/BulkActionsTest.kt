package dev.vinny.gsd.matrix

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.sync.RoomSyncLocalStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BulkActionsTest {
    private val fixed = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, fixed)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(title: String) =
        Task(title = title, urgent = false, important = false, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test
    fun bulkCompleteAndMoveAndTag() = runTest {
        val a = store.add(draft("a"))
        val b = store.add(draft("b"))
        val result = BulkActions.apply(store, setOf(a.id, b.id), BulkOp.Complete)
        assertEquals(2, result.succeeded)
        assertEquals(0, result.failed)
        assertTrue(store.observeTasks().first().all { it.completed })

        BulkActions.apply(store, setOf(a.id, b.id), BulkOp.MoveTo(dev.vinny.gsd.model.Quadrant.DO_FIRST))
        assertTrue(store.observeTasks().first().all { it.urgent && it.important })

        BulkActions.apply(store, setOf(a.id), BulkOp.AddTag("focus"))
        assertEquals(listOf("focus"), store.get(a.id)!!.tags)
        BulkActions.apply(store, setOf(a.id), BulkOp.RemoveTag("focus"))
        assertEquals(emptyList(), store.get(a.id)!!.tags)
    }

    @Test
    fun bulkSetDueAndDelete() = runTest {
        val a = store.add(draft("a"))
        val b = store.add(draft("b"))
        val due = fixed.instant().plusSeconds(86_400)
        BulkActions.apply(store, setOf(a.id, b.id), BulkOp.SetDue(due))
        assertTrue(store.observeTasks().first().all { it.dueDate == due })

        val result = BulkActions.apply(store, setOf(a.id, b.id), BulkOp.Delete)
        assertEquals(2, result.succeeded)
        assertEquals(0, store.observeTasks().first().size)
    }

    /** Review finding S6: 80 characters on iOS, 81 UTF-16 units here. Pull stores it without validating. */
    private suspend fun pulledOverTheLimit(tags: List<String> = emptyList()): Task {
        val task = draft("x".repeat(79) + "\uD83D\uDC4D")
            .copy(tags = tags, createdAt = fixed.instant(), updatedAt = fixed.instant())
        RoomSyncLocalStore(db).upsertFromRemote(task)
        return task
    }

    @Test
    fun bulkStateChangesWorkOnASyncedTaskOverTheLimit() = runTest {
        val task = pulledOverTheLimit(tags = listOf("focus"))
        val stateChanges = listOf(
            BulkOp.MoveTo(dev.vinny.gsd.model.Quadrant.DO_FIRST),
            BulkOp.RemoveTag("focus"),
            BulkOp.SetDue(fixed.instant().plusSeconds(86_400)),
            BulkOp.Complete,
        )

        stateChanges.forEach { op ->
            assertEquals(BulkResult(succeeded = 1, failed = 0), BulkActions.apply(store, setOf(task.id), op), "$op")
        }
    }

    @Test
    fun bulkAddTagStillValidatesBecauseTheTagIsTyped() = runTest {
        val task = pulledOverTheLimit()

        val result = BulkActions.apply(store, setOf(task.id), BulkOp.AddTag("focus"))

        assertEquals(BulkResult(succeeded = 0, failed = 1), result)
    }

    @Test
    fun partialFailureIsCounted() = runTest {
        val a = store.add(draft("a"))
        val result = BulkActions.apply(store, setOf(a.id, "missing-task-id-0000"), BulkOp.Complete)
        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
    }
}
