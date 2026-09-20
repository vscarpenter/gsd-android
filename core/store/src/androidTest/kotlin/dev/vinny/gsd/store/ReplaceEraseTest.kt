package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ReplaceEraseTest {
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

    private fun draft(id: String, title: String) =
        Task(id = id, title = title, urgent = false, important = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test
    fun replaceAllSwapsTableAndEnqueuesDeletesForMissing() = runTest {
        store.add(draft("keep-me-000000000000", "kept"))
        store.add(draft("drop-me-000000000000", "dropped"))
        store.replaceAll(listOf(
            draft("keep-me-000000000000", "kept v2"),
            draft("brand-new-0000000000", "new"),
        ))
        val titles = store.observeTasks().first().map { it.title }.sorted()
        assertEquals(listOf("kept v2", "new"), titles)
        val ops = db.syncQueueDao().pending()
        assertTrue(ops.any { it.operation == SyncOp.DELETE && it.taskId == "drop-me-000000000000" })
        assertTrue(ops.any { it.operation == SyncOp.UPDATE && it.taskId == "keep-me-000000000000" })
        assertTrue(ops.any { it.operation == SyncOp.UPDATE && it.taskId == "brand-new-0000000000" })
    }

    @Test
    fun eraseLocalClearsTasksArchiveQueueAndCustomViews() = runTest {
        store.add(draft("task-a-0000000000000", "a"))
        store.add(draft("task-b-0000000000000", "b"))
        store.delete("task-b-0000000000000")
        val archive = ArchiveStore(db, fixed)
        val views = SmartViewStore(db, fixed)
        views.seedBuiltInsIfEmpty()
        views.create("Custom", "bolt", dev.vinny.gsd.model.FilterCriteria())
        views.setPinned(listOf("today-focus"))

        LocalErase.eraseAll(db)

        assertEquals(0, store.observeTasks().first().size)
        assertEquals(0, archive.observeArchived().first().size)
        assertEquals(0, db.deletedTaskDao().allIds().size)
        assertEquals(0, db.syncQueueDao().pending().size)
        assertEquals(emptyList(), views.pinned())
        // built-ins survive; custom views are gone
        assertEquals(9, views.observeViews().first().size)
    }

    /**
     * The cursor means "everything up to here is on this device". An erase ends that. Clearing the
     * cursor in a later step left a window where a sync could run on a stale one (review finding S3).
     */
    @Test
    fun eraseClearsThePullCursorInTheSameStepAndKeepsTheDeviceIdentity() = runTest {
        db.syncMetadataDao().upsert(
            SyncMetadataEntity(cursor = "2026-09-20 12:00:00.000Z", deviceId = "device-1", deviceName = "Pixel"),
        )

        LocalErase.eraseAll(db)

        val metadata = db.syncMetadataDao().get()
        assertEquals(null, metadata?.cursor)
        assertEquals("device-1", metadata?.deviceId) // other devices use it to drop this device's echoes
    }
}
