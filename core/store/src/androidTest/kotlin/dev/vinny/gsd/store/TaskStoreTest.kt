package dev.vinny.gsd.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.model.TimeEntry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TaskStoreTest {
    private val fixed = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GsdDatabase::class.java,
        ).build()
        store = TaskStore(db, fixed)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(title: String = "t") =
        Task(
            title = title, urgent = false, important = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, // store re-stamps
        )

    @Test
    fun markReminderSentStaysOutOfLastWriteWins() = runTest {
        val added = store.add(draft("standup")) // stamped at the fixed clock, one CREATE row
        val firedAt = fixed.instant().plusSeconds(3600)
        val anHourLater = TaskStore(db, Clock.fixed(firedAt, ZoneOffset.UTC))

        anHourLater.markReminderSent(added.id, firedAt)

        val stored = store.get(added.id)!!
        assertTrue(stored.notificationSent)
        assertEquals(firedAt, stored.lastNotificationAt)
        assertEquals(added.updatedAt, stored.updatedAt) // device-local bookkeeping never moves the LWW key (spec §4.7)
        assertEquals(listOf(SyncOp.CREATE), db.syncQueueDao().pending().map { it.operation }) // and queues no push
    }

    @Test
    fun addStampsClockAndEnqueuesCreate() = runTest {
        val added = store.add(draft())
        assertEquals(fixed.instant(), added.createdAt)
        assertEquals(fixed.instant(), added.updatedAt)
        val queue = db.syncQueueDao().pending()
        assertEquals(1, queue.size)
        assertEquals(SyncOp.CREATE, queue.single().operation)
        assertEquals(added.id, queue.single().taskId)
    }

    @Test
    fun observeEmitsAfterMutation() = runTest {
        store.add(draft("visible"))
        assertEquals("visible", store.observeTasks().first().single().title)
    }

    @Test
    fun collectionsRoundTripThroughJsonColumns() = runTest {
        val rich = draft().copy(
            tags = listOf("work"),
            subtasks = listOf(Subtask("subid", "s")),
            dependencies = listOf("dep-id-1"),
            timeEntries = listOf(TimeEntry("abcd1234", fixed.instant())),
        )
        val loaded = store.get(store.add(rich).id)!!
        assertEquals(listOf("work"), loaded.tags)
        assertEquals("s", loaded.subtasks.single().title)
        assertEquals("dep-id-1", loaded.dependencies.single())
        assertEquals("abcd1234", loaded.timeEntries.single().id)
    }

    @Test
    fun setCompletedStampsCompletedAt() = runTest {
        val id = store.add(draft()).id
        assertEquals(fixed.instant(), store.setCompleted(id, true)!!.completedAt)
        assertNull(store.setCompleted(id, false)!!.completedAt)
    }

    @Test
    fun deleteScrubsDependenciesAndEnqueuesDelete() = runTest {
        val blocker = store.add(draft("blocker"))
        val blocked = store.add(draft("blocked").copy(dependencies = listOf(blocker.id)))
        store.delete(blocker.id)
        assertEquals(emptyList(), store.get(blocked.id)!!.dependencies)
        assertTrue(db.syncQueueDao().pending().any { it.operation == SyncOp.DELETE && it.taskId == blocker.id })
    }

    /**
     * Reproduces the benign race behind the delete-to-trash crash: two callers (e.g. an SSE
     * delete and a double-fired swipe) both read the row present outside the transaction, then
     * race into their own transactions — whichever runs second finds the row already gone. The
     * fixed code tolerates that; the old `checkNotNull` read crashed instead. Races many distinct
     * ids to make the interleaving likely without depending on a specific schedule.
     */
    @Test
    fun concurrentDeleteOfSameIdNeverThrows() = runTest {
        repeat(50) { i ->
            val id = store.add(draft("racy-$i")).id
            val results = listOf(
                async(Dispatchers.Default) { runCatching { store.delete(id) } },
                async(Dispatchers.Default) { runCatching { store.delete(id) } },
            ).awaitAll()
            results.forEach { it.getOrThrow() }
            assertEquals(1, db.deletedTaskDao().allIds().count { it == id })
        }
    }

    @Test
    fun validationFailureLeavesNoRowAndNoQueueEntry() = runTest {
        assertFailsWith<TaskValidationError.TitleLength> { store.add(draft(title = "")) }
        assertEquals(0, store.observeTasks().first().size)
        assertEquals(0, db.syncQueueDao().pending().size)
    }

    /**
     * Regression pin (parity T11): the first snapshot from `observeTasks()` arrives even with
     * no rows — Room emits the initial query rather than waiting for a write — so a `null`
     * initial value on a StateFlow wrapping this flips to the loaded empty list immediately.
     */
    @Test
    fun observeTasksEmitsEmptyListOnFreshDatabase() = runTest {
        assertEquals(emptyList(), store.observeTasks().first())
    }
}
