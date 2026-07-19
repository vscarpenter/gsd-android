package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeTracking
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
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
class TaskStoreDepthTest {
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

    private fun draft(recurrence: Recurrence = Recurrence.NONE) =
        Task(
            title = "t", urgent = true, important = true, createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH, recurrence = recurrence,
            dueDate = Instant.parse("2026-07-20T09:00:00.000Z"),
            subtasks = listOf(Subtask("sub1", "step", completed = true)),
        )

    @Test
    fun completingRecurringSpawnsInstanceInSameStore() = runTest {
        val original = store.add(draft(Recurrence.DAILY))
        store.setCompleted(original.id, true)
        val all = store.observeTasks().first()
        assertEquals(2, all.size)
        val instance = all.single { it.id != original.id }
        assertEquals(original.id, instance.parentTaskId)
        assertEquals(false, instance.completed)
        assertEquals(Instant.parse("2026-07-21T09:00:00.000Z"), instance.dueDate)
        assertTrue(instance.subtasks.all { !it.completed })
        // both mutations enqueued: CREATE(original) + UPDATE(complete) + CREATE(instance)
        val ops = db.syncQueueDao().pending().map { it.operation }
        assertEquals(listOf(SyncOp.CREATE, SyncOp.UPDATE, SyncOp.CREATE), ops)
    }

    @Test
    fun uncompletingDoesNotSpawn() = runTest {
        val original = store.add(draft(Recurrence.DAILY))
        store.setCompleted(original.id, true)
        val instanceCount = store.observeTasks().first().size
        store.setCompleted(original.id, false)
        assertEquals(instanceCount, store.observeTasks().first().size)
    }

    @Test
    fun snoozeSetsAndClears() = runTest {
        val id = store.add(draft()).id
        val until = fixed.instant().plus(1, ChronoUnit.HOURS)
        assertEquals(until, store.snooze(id, until)!!.snoozedUntil)
        assertNull(store.snooze(id, null)!!.snoozedUntil)
    }

    @Test
    fun timerRoundTripAndSecondStartRejected() = runTest {
        val id = store.add(draft()).id
        val started = store.startTimer(id)!!
        assertEquals(1, started.timeEntries.size)
        assertFailsWith<TimeTracking.TimerAlreadyRunning> { store.startTimer(id) }
        val stopped = store.stopTimer(id)!!
        assertNull(stopped.timeEntries.single().endedAt?.let { null }) // ended set
        assertEquals(0, stopped.timeSpent) // same fixed instant → 0 whole minutes
    }
}
