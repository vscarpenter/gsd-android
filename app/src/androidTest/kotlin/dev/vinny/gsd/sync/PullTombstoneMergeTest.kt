package dev.vinny.gsd.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeEntry
import dev.vinny.gsd.store.ArchiveStore
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Lists one record, the server copy another device edited. */
private class OneRecordRemote(private val record: WireTask) : SyncRemote {
    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage =
        TaskListPage(page = 1, totalPages = 1, items = listOf(record), updatedStamps = listOf(""))
    override suspend fun createTask(wire: WireTask): WireTask = wire.copy(id = "rec-new")
    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask = wire.copy(id = recordId)
    override suspend fun deleteTask(recordId: String) = Unit
    override suspend fun confirmIdentity(ownerId: String) = Unit
}

/**
 * S-IOS-02 through Room (spec §9.4): a server copy edited after this device archived or trashed the task brings
 * it back merged into that row, so the time-entry notes and the reminder this device already showed come back with
 * it. The test names are iOS #94's.
 */
@RunWith(AndroidJUnit4::class)
class PullTombstoneMergeTest {
    private val buriedAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS)
    private val clock = Clock.fixed(buriedAt, ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, clock)
    }

    @After
    fun tearDown() = db.close()

    /** A task with time-entry notes and a reminder this device already showed. */
    private suspend fun addWithDeviceFields(completed: Boolean): Task {
        val entry = TimeEntry("entry001", buriedAt.minusSeconds(1_200), buriedAt.minusSeconds(600), notes = "kept here")
        val added = store.add(Task(title = "before", urgent = true, important = true, createdAt = buriedAt, updatedAt = buriedAt,
            completed = completed, completedAt = buriedAt.takeIf { completed }, timeEntries = listOf(entry)))
        store.markReminderSent(added.id, buriedAt)
        return checkNotNull(store.get(added.id))
    }

    /** One run against a server copy edited 30 minutes after the task was buried here. */
    private suspend fun pullEditedElsewhere(task: Task): Task? {
        db.syncQueueDao().clear() // the bury's own push already ran, and the server kept the newer edit (B-47)
        val edited = task.copy(title = "edited elsewhere", updatedAt = buriedAt.plus(30, ChronoUnit.MINUTES))
        val remote = OneRecordRemote(WireMapper.push(edited, "other-device", "user-1", defaultReminder = 15))
        val local = RoomSyncLocalStore(db)
        SyncEngine(remote, local, Clock.systemUTC(), throttleMs = 0, ownerId = { "user-1" }).runSync(triggeredByUser = false)
        return local.taskById(task.id)
    }

    @Test
    fun anArchivedTaskPulledBackKeepsThisDevicesFields() = runBlocking {
        val task = addWithDeviceFields(completed = true)
        ArchiveStore(db, clock).archive(task.id)

        val back = pullEditedElsewhere(task)

        assertEquals("edited elsewhere", back?.title)
        assertEquals("kept here", back?.timeEntries?.single()?.notes)
        assertEquals(true, back?.notificationSent)
        assertEquals(buriedAt, back?.lastNotificationAt)
    }

    @Test
    fun aTrashedTaskPulledBackKeepsThisDevicesFields() = runBlocking {
        val task = addWithDeviceFields(completed = false)
        store.delete(task.id)

        val back = pullEditedElsewhere(task)

        assertEquals("edited elsewhere", back?.title)
        assertEquals("kept here", back?.timeEntries?.single()?.notes)
        assertEquals(true, back?.notificationSent)
        assertEquals(buriedAt, back?.lastNotificationAt)
    }
}
