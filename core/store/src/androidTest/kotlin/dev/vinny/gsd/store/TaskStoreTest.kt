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

    @Test
    fun validationFailureLeavesNoRowAndNoQueueEntry() = runTest {
        assertFailsWith<TaskValidationError.TitleLength> { store.add(draft(title = "")) }
        assertEquals(0, store.observeTasks().first().size)
        assertEquals(0, db.syncQueueDao().pending().size)
    }
}
