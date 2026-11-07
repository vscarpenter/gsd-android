package dev.vinny.gsd.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.model.TimeEntry
import java.time.Clock
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TaskStoreTest {
    private val fixed = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GsdDatabase::class.java,
        ).build()
        store = TaskStore(db, fixed)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(title: String = "t") =
        Task(
            title = title, urgent = false, important = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, // store re-stamps
        )

    @Test
    fun markReminderSentStaysOutOfLastWriteWins() = runTest {
        val added = store.add(draft("standup")) // stamped at the fixed clock, one CREATE row
        val firedAt = fixed.instant().plusSeconds(3600)
        val anHourLater = TaskStore(db, Clock.fixed(firedAt, ZoneOffset.UTC))

        anHourLater.markReminderSent(added.id, firedAt)

        val stored = store.get(added.id)!!
        assertTrue(stored.notificationSent)
        assertEquals(firedAt, stored.lastNotificationAt)
        assertEquals(added.updatedAt, stored.updatedAt) // device-local bookkeeping never moves the LWW key (spec §4.7)
        assertEquals(listOf(SyncOp.CREATE), db.syncQueueDao().pending().map { it.operation }) // and queues no push
    }

    @Test
    fun addStampsClockAndEnqueuesCreate() = runTest {
        val added = store.add(draft())
        assertEquals(fixed.instant(), added.createdAt)
        assertEquals(fixed.instant(), added.updatedAt)
        val queue = db.syncQueueDao().pending()
        assertEquals(1, queue.size)
        assertEquals(SyncOp.CREATE, queue.single().operation)
        assertEquals(added.id, queue.single().taskId)
    }

    @Test
    fun observeEmitsAfterMutation() = runTest {
        store.add(draft("visible"))
        assertEquals("visible", store.observeTasks().first().single().title)
    }

    @Test
    fun collectionsRoundTripThroughJsonColumns() = runTest {
        val rich = draft().copy(
            tags = listOf("work"),
            subtasks = listOf(Subtask("subid", "s")),
            dependencies = listOf("dep-id-1"),
            timeEntries = listOf(TimeEntry("abcd1234", fixed.instant())),
        )
        val loaded = store.get(store.add(rich).id)!!
        assertEquals(listOf("work"), loaded.tags)
        assertEquals("s", loaded.subtasks.single().title)
        assertEquals("dep-id-1", loaded.dependencies.single())
        assertEquals("abcd1234", loaded.timeEntries.single().id)
    }

    @Test
    fun setCompletedStampsCompletedAt() = runTest {
        val id = store.add(draft()).id
        assertEquals(fixed.instant(), store.setCompleted(id, true)!!.completedAt)
        assertNull(store.setCompleted(id, false)!!.completedAt)
    }

    @Test
    fun deleteScrubsDependenciesAndEnqueuesDelete() = runTest {
        val blocker = store.add(draft("blocker"))
        val blocked = store.add(draft("blocked").copy(dependencies = listOf(blocker.id)))
        store.delete(blocker.id)
        assertEquals(emptyList(), store.get(blocked.id)!!.dependencies)
        assertTrue(db.syncQueueDao().pending().any { it.operation == SyncOp.DELETE && it.taskId == blocker.id })
    }

    /**
     * Reproduces the benign race behind the delete-to-trash crash: two callers (e.g. an SSE
     * delete and a double-fired swipe) both read the row present outside the transaction, then
     * race into their own transactions — whichever runs second finds the row already gone. The
     * fixed code tolerates that; the old `checkNotNull` read crashed instead. Races many distinct
     * ids to make the interleaving likely without depending on a specific schedule.
     */
    @Test
    fun concurrentDeleteOfSameIdNeverThrows() = runTest {
        repeat(50) { i ->
            val id = store.add(draft("racy-$i")).id
            val results = listOf(
                async(Dispatchers.Default) { runCatching { store.delete(id) } },
                async(Dispatchers.Default) { runCatching { store.delete(id) } },
            ).awaitAll()
            results.forEach { it.getOrThrow() }
            assertEquals(1, db.deletedTaskDao().allIds().count { it == id })
        }
    }

    @Test
    fun validationFailureLeavesNoRowAndNoQueueEntry() = runTest {
        assertFailsWith<TaskValidationError.TitleLength> { store.add(draft(title = "")) }
        assertEquals(0, store.observeTasks().first().size)
        assertEquals(0, db.syncQueueDao().pending().size)
    }

    /**
     * Regression pin (parity T11): the first snapshot from `observeTasks()` arrives even with
     * no rows — Room emits the initial query rather than waiting for a write — so a `null`
     * initial value on a StateFlow wrapping this flips to the loaded empty list immediately.
     */
    @Test
    fun observeTasksEmitsEmptyListOnFreshDatabase() = runTest {
        assertEquals(emptyList(), store.observeTasks().first())
    }

    // --- D-10: a task this build writes stores a number (web `tasks/spec-notify-before.md`, section 2) ---

    private suspend fun setDefaultReminder(minutes: Int) = db.notificationSettingsDao().upsert(
        NotificationSettingsEntity(
            enabled = true, defaultReminder = minutes, soundEnabled = true,
            quietHoursStart = null, quietHoursEnd = null, permissionAsked = false, updatedAt = 0L,
        ),
    )

    private suspend fun queuedOffsets(id: String): List<Int?> = db.syncQueueDao().pending()
        .filter { it.taskId == id }
        .map { row -> row.payloadJson?.let { TaskJson.decodeOrNull(TaskJson.json.parseToJsonElement(it)) }?.notifyBefore }

    /** Spec name: `a new task with no offset stores the default`. */
    @Test
    fun aNewTaskWithNoOffsetStoresTheDefault() = runTest {
        setDefaultReminder(30)

        val added = store.add(draft())

        assertEquals(30, added.notifyBefore)
        assertEquals(30, store.get(added.id)?.notifyBefore)
        assertEquals(listOf<Int?>(30), queuedOffsets(added.id))
    }

    /**
     * Spec name: `any write of a legacy null pushes the resolved number`. A row stored before this build can
     * hold null. Whatever write reaches it next stores this device's default and queues that number.
     */
    @Test
    fun anyWriteOfALegacyNullPushesTheResolvedNumber() = runTest {
        setDefaultReminder(45)
        val writes: List<Pair<String, suspend (String) -> Task?>> = listOf(
            "update" to { id -> store.update(store.get(id)!!.copy(title = "updated")) },
            "edit" to { id -> store.edit(id, store.get(id)!!.copy(title = "edited")) { it.copy(title = "edited") } },
            "change" to { id -> store.change(id) { it.copy(urgent = true) } },
            "setCompleted" to { id -> store.setCompleted(id, completed = true) },
            "duplicate" to { id -> store.duplicate(id) },
        )
        writes.forEachIndexed { index, (name, write) ->
            val legacy = draft("legacy $name").copy(id = "legacy-offset-$index", createdAt = fixed.instant(), updatedAt = fixed.instant())
            db.taskDao().upsert(legacy.toEntity()) // as a build before D-10 stored it: no offset

            val written = write(legacy.id)!!

            assertEquals(45, written.notifyBefore, "$name stored")
            assertEquals(45, store.get(written.id)?.notifyBefore, "$name row")
            assertEquals(listOf<Int?>(45), queuedOffsets(written.id), "$name queued")
        }
    }

    /** The drain's write-back (D-10, section 3): device-local, like reminder bookkeeping. */
    @Test
    fun settlingANullOffsetKeepsTheStampAndQueuesNothing() = runTest {
        val legacy = draft("legacy").copy(createdAt = fixed.instant(), updatedAt = fixed.instant())
        val numbered = draft("numbered").copy(createdAt = fixed.instant(), updatedAt = fixed.instant(), notifyBefore = 20)
        db.taskDao().upsert(legacy.toEntity())
        db.taskDao().upsert(numbered.toEntity())

        db.taskDao().settleNotifyBefore(legacy.id, 30)
        db.taskDao().settleNotifyBefore(numbered.id, 30)

        assertEquals(30, store.get(legacy.id)?.notifyBefore)
        assertEquals(legacy.updatedAt.toEpochMilli(), store.get(legacy.id)?.updatedAt?.toEpochMilli())
        assertEquals(20, store.get(numbered.id)?.notifyBefore) // a number written since the push stays
        assertTrue(db.syncQueueDao().pending().isEmpty())
    }

    /** Top three: a pick survives the store, an archive and its restore, and Trash and its restore (spec §7). */
    @Test
    fun aPickSurvivesTheStoreTheArchiveAndTrash() = runTest {
        val picked = store.add(draft("picked").copy(focusDate = "2026-07-19", focusRank = 2))
        assertEquals("2026-07-19" to 2, store.get(picked.id)?.let { it.focusDate to it.focusRank })

        val archive = ArchiveStore(db, fixed)
        store.setCompleted(picked.id, completed = true) // only a completed task archives
        archive.archive(picked.id)
        assertEquals("2026-07-19", db.archivedTaskDao().byId(picked.id)?.focusDate)
        archive.restore(picked.id)
        assertEquals("2026-07-19" to 2, store.get(picked.id)?.let { it.focusDate to it.focusRank })

        store.delete(picked.id)
        assertEquals(2, db.deletedTaskDao().byId(picked.id)?.focusRank)
        TrashStore(db, fixed).restore(picked.id)
        assertEquals("2026-07-19" to 2, store.get(picked.id)?.let { it.focusDate to it.focusRank })
    }
}
