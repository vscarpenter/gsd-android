package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
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
class ArchiveStoreTest {
    private val fixed = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private lateinit var archive: ArchiveStore

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, fixed)
        archive = ArchiveStore(db, fixed)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(title: String = "t") =
        Task(title = title, urgent = false, important = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test
    fun archiveMovesCompletedTaskAndEnqueuesDelete() = runTest {
        val added = store.add(draft())
        store.setCompleted(added.id, true)
        archive.archive(added.id)
        assertNull(store.get(added.id))
        assertEquals(1, archive.observeArchived().first().size)
        assertTrue(db.syncQueueDao().pending().any { it.operation == SyncOp.DELETE && it.taskId == added.id })
    }

    @Test
    fun archiveRefusesIncompleteTask() = runTest {
        val added = store.add(draft())
        assertFailsWith<ArchiveStore.NotCompleted> { archive.archive(added.id) }
    }

    @Test
    fun restoreRecreatesActiveTaskAndEnqueuesCreate() = runTest {
        val added = store.add(draft("restore me"))
        store.setCompleted(added.id, true)
        archive.archive(added.id)
        archive.restore(added.id)
        assertEquals("restore me", store.get(added.id)!!.title)
        assertEquals(0, archive.observeArchived().first().size)
        // last queue row is the restore CREATE
        assertEquals(SyncOp.CREATE, db.syncQueueDao().pending().last().operation)
    }

    @Test
    fun deletePermanentlyRemovesWithoutSync() = runTest {
        val added = store.add(draft())
        store.setCompleted(added.id, true)
        archive.archive(added.id)
        val queueBefore = db.syncQueueDao().pending().size
        archive.deletePermanently(added.id)
        assertEquals(0, archive.observeArchived().first().size)
        assertEquals(queueBefore, db.syncQueueDao().pending().size)
    }

    @Test
    fun sweepArchivesOnlyOldCompleted() = runTest {
        val oldDone = store.add(draft("old done"))
        // complete, then age the row by rewriting completedAt far in the past
        store.setCompleted(oldDone.id, true)
        store.update(store.get(oldDone.id)!!.copy(completedAt = fixed.instant().minus(40, ChronoUnit.DAYS)))
        val freshDone = store.add(draft("fresh done"))
        store.setCompleted(freshDone.id, true)
        store.add(draft("active"))

        val swept = archive.sweep(archiveAfterDays = 30)
        assertEquals(1, swept)
        assertNull(store.get(oldDone.id))
        assertEquals("fresh done", store.get(freshDone.id)!!.title)
    }
}
