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
    private val seededAt = Instant.parse("2026-07-19T12:00:00.000Z")
    private var now = seededAt

    /** A clock the test can move, so the stamp an edit writes can be told apart from the seed's. */
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
    }
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

    private suspend fun seed(): Task =
        store.add(Task(title = "edit me", urgent = true, important = true, createdAt = now, updatedAt = now))

    /** Found by the reviewer of #22: nothing pinned the queue row, so an edit that never synced stayed green. */
    @Test
    fun anEditQueuesOnePush(): Unit = runBlocking {
        val task = seed()
        val queuedBefore = db.syncQueueDao().countForTask(task.id)

        store.edit(task.id, typed = task.copy(title = "edited")) { it.copy(title = "edited") }

        assertEquals(queuedBefore + 1, db.syncQueueDao().countForTask(task.id))
    }

    /** The stamp is the last-write-wins key, so a save that kept the old one would lose to every other device. */
    @Test
    fun anEditStampsTheTaskWithTheTimeOfTheWrite(): Unit = runBlocking {
        val task = seed()
        now = seededAt.plusSeconds(60)

        store.edit(task.id, typed = task.copy(title = "edited")) { it.copy(title = "edited") }

        assertEquals(seededAt.plusSeconds(60), store.get(task.id)?.updatedAt)
    }

    /** Eight at once, so a read outside the transaction cannot pass by luck (the S5 lesson). */
    @Test
    fun concurrentEditsEachSeeTheOneBefore(): Unit = runBlocking {
        val task = seed()

        (1..8).map { n ->
            async(Dispatchers.IO) { store.edit(task.id, typed = task.copy(tags = listOf("tag$n"))) { it.copy(tags = it.tags + "tag$n") } }
        }.awaitAll()

        assertEquals((1..8).map { "tag$it" }.toSet(), store.get(task.id)?.tags?.toSet())
    }

    @Test
    fun anEditStillValidatesWhatTheUserTyped(): Unit = runBlocking {
        val task = seed()

        assertFailsWith<TaskValidationError> {
            store.edit(task.id, typed = task.copy(title = "x".repeat(81))) { it.copy(title = "x".repeat(81)) }
        }

        assertEquals("edit me", store.get(task.id)?.title) // the rejected edit wrote nothing
    }

    /**
     * Found by the reviewer of #22: a title that arrives by sync while the sheet is open can break the
     * limit, because iOS counts characters and Android counts UTF-16 units. The user never touched it
     * and the sheet shows the old, legal one, so it must not fail the save.
     */
    @Test
    fun aSyncedFieldTheUserNeverTouchedDoesNotFailTheSave(): Unit = runBlocking {
        val opened = seed()
        val synced = "\uD83D\uDE00".repeat(41) // 41 emoji: 82 UTF-16 units
        store.change(opened.id) { it.copy(title = synced) } // what sync may write; it only normalizes

        val typed = opened.copy(description = "notes")
        store.edit(opened.id, typed) { current -> dev.vinny.gsd.model.EditorRules.merge(current, opened, typed) }

        val saved = store.get(opened.id)
        assertEquals("notes", saved?.description)
        assertEquals(synced, saved?.title)
    }

    @Test
    fun anEditOfAVanishedTaskWritesNothing(): Unit = runBlocking {
        val task = seed()
        store.delete(task.id)
        val queuedBefore = db.syncQueueDao().countForTask(task.id)

        assertNull(store.edit(task.id, typed = task.copy(title = "too late")) { it.copy(title = "too late") })

        assertNull(store.get(task.id))
        assertEquals(queuedBefore, db.syncQueueDao().countForTask(task.id))
        assertEquals(0, store.observeTasks().first().size)
    }
}
