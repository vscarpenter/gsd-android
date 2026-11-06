package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The ABANDON verdict's transaction (spec 9.4). Reconciliation decides from a queue read made before
 * the identity check, so the row it acts on can have changed by the time the transaction runs.
 */
@RunWith(AndroidJUnit4::class)
class AbandonToTrashTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var taskStore: TaskStore

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        taskStore = TaskStore(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(id: String) =
        Task(id = id, title = "t", urgent = false, important = false, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    /** Sprint backlog RECONCILE-RECHECK: a pending row means the user edited since the verdict. The push lands it. */
    @Test
    fun aPendingRowQueuedSinceTheVerdictKeepsTheTaskActive() = runTest {
        val task = taskStore.add(draft("edited-since")) // add queues a pending row for it

        db.abandonToTrash(task.id, clock.millis())

        assertEquals(task.id, db.taskDao().byId(task.id)?.id)
        assertNull(db.deletedTaskDao().byId(task.id))
        assertEquals(1, db.syncQueueDao().pending().count { it.taskId == task.id })
    }

    /** The verdict's own case: every row failed, so the content never reached the server. */
    @Test
    fun aTaskWithOnlyFailedRowsMovesToTrashAndDropsItsRows() = runTest {
        val task = taskStore.add(draft("failed-only"))
        val row = db.syncQueueDao().pending().single { it.taskId == task.id }
        db.syncQueueDao().bumpRetry(row.id, 5, "boom", clock.millis(), SyncQueueEntity.STATUS_FAILED, clock.millis())

        db.abandonToTrash(task.id, clock.millis())

        assertNull(db.taskDao().byId(task.id))
        assertEquals(task.id, db.deletedTaskDao().byId(task.id)?.id)
        assertEquals(0, db.syncQueueDao().countForTask(task.id))
    }
}
