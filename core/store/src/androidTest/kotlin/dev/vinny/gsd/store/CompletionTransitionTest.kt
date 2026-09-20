package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.Task
import java.time.Clock
import java.time.Duration
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

/**
 * Review finding S5: completion is a transition. Asking for the state a task already has must
 * write nothing, or a stale notification action duplicates a recurring series.
 */
@RunWith(AndroidJUnit4::class)
class CompletionTransitionTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private var now: Instant = t0
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(
            db,
            object : Clock() {
                override fun instant(): Instant = now
                override fun getZone() = ZoneOffset.UTC
                override fun withZone(zone: java.time.ZoneId) = this
            },
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun dailyTask(): Task = store.add(
        Task(
            title = "standup", urgent = true, important = true, recurrence = Recurrence.DAILY,
            dueDate = t0.plus(Duration.ofDays(1)), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        ),
    )

    @Test
    fun completingAnAlreadyCompletedRecurringTaskSpawnsNothing() = runTest {
        val task = dailyTask()
        store.setCompleted(task.id, true)

        store.setCompleted(task.id, true) // a stale notification action, or bulk Done over a finished task

        assertEquals(2, store.observeTasks().first().size) // the original and one instance, not two
    }

    @Test
    fun aRedundantCompletionKeepsTheRealCompletionTimeAndQueuesNothing() = runTest {
        val task = dailyTask()
        store.setCompleted(task.id, true)
        val queuedBefore = db.syncQueueDao().pending().size
        now = t0.plus(Duration.ofHours(3))

        val again = store.setCompleted(task.id, true)

        assertEquals(t0, again?.completedAt)
        assertEquals(t0, store.get(task.id)?.updatedAt) // a restamp here would win last-write-wins elsewhere
        assertEquals(queuedBefore, db.syncQueueDao().pending().size)
    }

    @Test
    fun reopeningATaskThatIsAlreadyOpenQueuesNothing() = runTest {
        val task = dailyTask()
        val queuedBefore = db.syncQueueDao().pending().size
        now = t0.plus(Duration.ofHours(3))

        val same = store.setCompleted(task.id, false)

        assertEquals(t0, same?.updatedAt)
        assertEquals(queuedBefore, db.syncQueueDao().pending().size)
    }

    @Test
    fun aDoubleFiredCompleteSpawnsOnce() = runTest {
        val task = dailyTask()

        List(8) { async(Dispatchers.Default) { store.setCompleted(task.id, true) } }.awaitAll()

        assertEquals(2, store.observeTasks().first().size)
    }
}
