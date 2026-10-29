package dev.vinny.gsd.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.SavedPlacement
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Saved through the single mutation path (spec 2026-10-29 §3.3). */
@RunWith(AndroidJUnit4::class)
class TaskStoreSavedTest {
    private val now = Instant.parse("2026-10-29T15:00:00.000Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GsdDatabase::class.java).build()
        store = TaskStore(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(recurrence: Recurrence = Recurrence.NONE): Task = store.add(
        Task(title = "keep for later", urgent = false, important = true, recurrence = recurrence,
            dueDate = now.plusSeconds(86_400), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
    )

    private suspend fun lastQueued(id: String): Task =
        TaskJson.decodeOrNull(TaskJson.json.parseToJsonElement(db.syncQueueDao().pending().last { it.taskId == id }.payloadJson!!))!!

    @Test
    fun moveToSavedStampsPlacementAndQueuesIt(): Unit = runBlocking {
        val task = seed()
        val saved = store.moveToSaved(task.id)!!
        assertEquals(now, saved.savedAt)
        assertEquals(now, store.get(task.id)!!.savedAt)
        assertEquals(now, lastQueued(task.id).savedAt)
    }

    @Test
    fun aRunningTimerBlocksSaving(): Unit = runBlocking {
        val task = seed()
        store.startTimer(task.id)
        assertFailsWith<SavedPlacement.CannotSave> { store.moveToSaved(task.id) }
        assertNull(store.get(task.id)!!.savedAt)
    }

    @Test
    fun aSavedTaskRefusesCompletionTimersAndSnooze(): Unit = runBlocking {
        val task = seed(recurrence = Recurrence.WEEKLY)
        store.moveToSaved(task.id)
        val queued = db.syncQueueDao().pending().size

        store.setCompleted(task.id, true)
        store.startTimer(task.id)
        store.snooze(task.id, now.plusSeconds(3_600))

        val after = store.get(task.id)!!
        assertFalse(after.completed)
        assertTrue(after.timeEntries.isEmpty())
        assertNull(after.snoozedUntil)
        assertEquals(1, db.taskDao().all().size) // no recurrence spawned
        assertEquals(queued, db.syncQueueDao().pending().size) // nothing written
    }

    @Test
    fun returnToMatrixAppliesTheReviewAndClearsPlacement(): Unit = runBlocking {
        val task = seed()
        store.moveToSaved(task.id)
        val due = Instant.parse("2026-11-02T06:00:00.000Z")
        val back = store.returnToMatrix(task.id, urgent = true, important = true, dueDate = due)!!
        assertNull(back.savedAt)
        assertEquals(Quadrant.DO_FIRST, back.quadrant)
        assertEquals(due, back.dueDate)
        assertNull(lastQueued(task.id).savedAt)
    }

    @Test
    fun editingNotesChangesOnlyTheDescription(): Unit = runBlocking {
        val task = seed()
        store.moveToSaved(task.id)
        val noted = store.editNotes(task.id, "https://example.com and a thought")!!
        assertEquals("https://example.com and a thought", noted.description)
        assertEquals(now, noted.savedAt)
        assertEquals(task.dueDate, noted.dueDate)
    }

    @Test
    fun placementSurvivesTheTrash(): Unit = runBlocking {
        val task = seed()
        store.moveToSaved(task.id)
        store.delete(task.id)
        assertEquals(now, TrashStore(db, clock).byId(task.id)!!.savedAt)
    }
}
