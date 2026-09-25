package dev.vinny.gsd.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * "Worth a look" in the 2026-09-20 review: `change` read the row outside its transaction, so a snooze
 * or a timer write could overwrite an editor save that committed a moment earlier. It now reads inside
 * the transaction it writes in, as `edit` and `setCompleted` do.
 */
@RunWith(AndroidJUnit4::class)
class TaskStoreChangeTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GsdDatabase::class.java,
        ).build()
        store = TaskStore(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(): Task = store.add(
        Task(title = "change me", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
    )

    /** Eight at once, so a read outside the transaction cannot pass by luck (the S5 lesson). */
    @Test
    fun concurrentChangesEachSeeTheOneBefore(): Unit = runBlocking {
        val task = seed()

        (1..8).map { n ->
            async(Dispatchers.IO) { store.change(task.id) { it.copy(tags = it.tags + "tag$n") } }
        }.awaitAll()

        assertEquals((1..8).map { "tag$it" }.toSet(), store.get(task.id)?.tags?.toSet())
    }

    /** Pins the plumbing: with the queue insert gone, the race test above still passes. */
    @Test
    fun aChangeQueuesOnePush(): Unit = runBlocking {
        val task = seed()
        val queuedBefore = db.syncQueueDao().countForTask(task.id)

        store.change(task.id) { it.copy(urgent = false) }

        assertEquals(queuedBefore + 1, db.syncQueueDao().countForTask(task.id))
    }
}
