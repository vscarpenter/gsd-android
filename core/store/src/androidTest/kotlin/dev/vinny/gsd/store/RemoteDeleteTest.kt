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
 * The APPLY verdict's write (spec 9.4), and the realtime delete's. Both decide from a queue read made
 * earlier, so the row they act on can have gained a pending edit by the time the write runs.
 */
@RunWith(AndroidJUnit4::class)
class RemoteDeleteTest {
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

    /** Sprint backlog RECONCILE-RECHECK, the delete half: a pending row means the user edited since the verdict. */
    @Test
    fun aPendingRowQueuedSinceTheVerdictKeepsTheTaskActive() = runTest {
        val task = taskStore.add(draft("edited-since")) // add queues a pending row for it

        db.deleteFromRemote(task.id)

        assertEquals(task.id, db.taskDao().byId(task.id)?.id)
        assertEquals(1, db.syncQueueDao().pending().count { it.taskId == task.id })
    }

    /** The verdict's own case: nothing queued, so the remote delete lands and leaves no Trash copy (spec 8.20). */
    @Test
    fun aTaskWithNoQueueRowIsDeletedWithoutATrashCopy() = runTest {
        val task = taskStore.add(draft("gone-remotely"))
        db.syncQueueDao().deleteForTask(task.id) // as a pushed create leaves it

        db.deleteFromRemote(task.id)

        assertNull(db.taskDao().byId(task.id))
        assertNull(db.deletedTaskDao().byId(task.id))
    }
}
