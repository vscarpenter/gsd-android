package dev.vinny.gsd.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.model.TimeTracking
import java.time.Clock
import java.time.Duration
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Review finding S6: iOS counts characters and Android counts UTF-16 units, so a title that is
 * legal on iOS can arrive over Android's limit. Pull stores it as is. Every later touch must work.
 */
@RunWith(AndroidJUnit4::class)
class SyncedTaskOverLimitTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    /** 80 characters on iOS. The emoji is two UTF-16 units, so Android sees 81. */
    private val legalOnIos = "x".repeat(79) + "👍"

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

    /** Lands the way a pulled task does: straight into the table, with no validation. */
    private suspend fun pulled(recurrence: Recurrence = Recurrence.NONE): Task {
        val task = Task(
            title = legalOnIos, urgent = true, important = true, recurrence = recurrence,
            dueDate = now.plus(Duration.ofDays(1)), createdAt = now, updatedAt = now,
        )
        db.taskDao().upsert(task.toEntity())
        return task
    }

    @Test
    fun itCanBeCompleted() = runTest {
        val task = pulled()

        val done = store.setCompleted(task.id, true)

        assertEquals(true, done?.completed)
    }

    @Test
    fun completingARecurringOneStillSpawnsTheNextInstance() = runTest {
        val task = pulled(Recurrence.DAILY)

        store.setCompleted(task.id, true)

        val all = store.observeTasks().first()
        assertEquals(2, all.size)
        assertTrue(all.single { it.id == task.id }.completed)
        assertEquals(legalOnIos, all.single { it.id != task.id }.title)
    }

    @Test
    fun itCanBeSnoozedAndTheSnoozeIsStillCapped() = runTest {
        val task = pulled()

        val snoozed = store.snooze(task.id, now.plus(Duration.ofDays(400)))

        assertEquals(now.plus(Duration.ofDays(365)), snoozed?.snoozedUntil)
    }

    @Test
    fun itsTimerCanStartAndStop() = runTest {
        val task = pulled()

        store.startTimer(task.id)
        assertNotNull(TimeTracking.runningEntry(store.get(task.id)!!))
        val stopped = store.stopTimer(task.id)

        assertEquals(1, stopped?.timeEntries?.size)
    }

    @Test
    fun itCanBeMovedAndTheMoveIsQueued() = runTest {
        val task = pulled()

        val moved = store.change(task.id) { it.copy(urgent = false, important = false) }

        assertEquals(false, moved?.urgent)
        assertEquals(false, store.get(task.id)?.important)
        assertEquals(listOf(SyncOp.UPDATE to task.id), db.syncQueueDao().pending().map { it.operation to it.taskId })
    }

    @Test
    fun itCanBeDuplicatedAndTheCopyIsQueued() = runTest {
        val task = pulled()

        val copy = store.duplicate(task.id)

        assertNotNull(copy)
        assertEquals(legalOnIos, store.get(copy.id)?.title)
        assertEquals(listOf(SyncOp.CREATE to copy.id), db.syncQueueDao().pending().map { it.operation to it.taskId })
    }

    @Test
    fun aChangeOrACopyOfAMissingTaskWritesNothing() = runTest {
        assertEquals(null, store.change("gone-task") { it.copy(urgent = true) })
        assertEquals(null, store.duplicate("gone-task"))

        assertEquals(0, db.syncQueueDao().pending().size)
        assertEquals(0, store.observeTasks().first().size)
    }

    @Test
    fun anEditStillHasToFitTheLimit() = runTest {
        val task = pulled()

        assertFailsWith<TaskValidationError.TitleLength> { store.update(task.copy(description = "typed here")) }
    }
}
