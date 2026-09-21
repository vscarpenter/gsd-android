package dev.vinny.gsd.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidationError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Review finding 44: an editor save must apply to the task as it is when the write happens, not as
 * it was when the sheet opened. So `edit` reads the row inside the transaction it writes in.
 */
@RunWith(AndroidJUnit4::class)
class TaskStoreEditTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GsdDatabase::class.java,
        ).build()
        store = TaskStore(db, Clock.fixed(now, ZoneOffset.UTC))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(): Task =
        store.add(Task(title = "edit me", urgent = true, important = true, createdAt = now, updatedAt = now))

    /** Eight at once, so a read outside the transaction cannot pass by luck (the S5 lesson). */
    @Test
    fun concurrentEditsEachSeeTheOneBefore(): Unit = runBlocking {
        val task = seed()

        (1..8).map { n ->
            async(Dispatchers.IO) { store.edit(task.id) { it.copy(tags = it.tags + "tag$n") } }
        }.awaitAll()

        assertEquals((1..8).map { "tag$it" }.toSet(), store.get(task.id)?.tags?.toSet())
    }

    @Test
    fun anEditStillValidatesWhatTheUserTyped(): Unit = runBlocking {
        val task = seed()

        assertFailsWith<TaskValidationError> { store.edit(task.id) { it.copy(title = "x".repeat(81)) } }

        assertEquals("edit me", store.get(task.id)?.title) // the rejected edit wrote nothing
    }

    @Test
    fun anEditOfAVanishedTaskWritesNothing(): Unit = runBlocking {
        val task = seed()
        store.delete(task.id)
        val queuedBefore = db.syncQueueDao().countForTask(task.id)

        assertNull(store.edit(task.id) { it.copy(title = "too late") })

        assertNull(store.get(task.id))
        assertEquals(queuedBefore, db.syncQueueDao().countForTask(task.id))
        assertEquals(0, store.observeTasks().first().size)
    }
}
