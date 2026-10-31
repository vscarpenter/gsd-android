package dev.vinny.gsd.sync

import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.store.SyncOp
import dev.vinny.gsd.store.SyncQueueEntity
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity backlog B-14 (web #538, `lib/sync/sync-session.ts`): a sync belongs to the account signed in
 * when it started, and stops writing once that session ends. B-42 (web `isStaleDelete`): a queued delete
 * is stale when the server copy changed after it was queued.
 */
class SyncSafetyTest {
    private val now = Instant.parse("2026-10-29T15:00:00.000Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private var owner: String? = "user-1"

    private fun task(id: String, title: String = "t", updatedAt: Instant = now) =
        Task(id = id, title = title, urgent = false, important = false,
            createdAt = now.minusSeconds(600), updatedAt = updatedAt)

    private fun engine(remote: FakeRemote, local: FakeLocal) =
        SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { owner })

    private fun queueItem(id: Long, task: Task, op: SyncOp, queuedAt: Instant = now) = SyncQueueEntity(
        id = id, taskId = task.id, operation = op, timestamp = queuedAt.toEpochMilli(),
        payloadJson = if (op == SyncOp.DELETE) null else TaskJson.encode(task),
    )

    /** Ends the session as the listing call number [onCall] answers, the way a sign-out lands mid-run. */
    private fun FakeRemote.endSessionOnListing(onCall: Int, nextOwner: String?) {
        var calls = 0
        listResponder = { _, _ ->
            calls++
            if (calls == onCall) owner = nextOwner
            val items = records.entries.sortedBy { it.key }.map { it.value.copy(id = it.key) }
            TaskListPage(page = 1, totalPages = 1, items = items,
                updatedStamps = items.map { PbDates.formatSpace(PbDates.parse(it.clientUpdatedAt)!!) })
        }
    }

    @Test
    fun `a sign-out during the pull writes nothing it pulled`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("from-old-account"), "other", "user-1")
        remote.endSessionOnListing(onCall = 1, nextOwner = null)

        val result = engine(remote, local).runSync(false)

        assertTrue(local.tasks.isEmpty())
        assertNull(local.savedCursor)
        assertEquals("error", result.status)
        assertFalse(result.notSignedIn) // the coordinator already ended the session; nothing more to do
    }

    @Test
    fun `an account switch during the pull writes nothing from the old account`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("old-account-task"), "other", "user-1")
        remote.endSessionOnListing(onCall = 1, nextOwner = "user-2")

        engine(remote, local).runSync(false)

        assertTrue(local.tasks.isEmpty())
    }

    @Test
    fun `a task created during reconciliation's task read survives`() = runTest {
        val created = task("just-made")
        val local = object : FakeLocal() {
            /** A capture landing as reconciliation reads: the queue row and the task commit together. */
            override suspend fun activeTasks(): List<Task> {
                val snapshot = super.activeTasks()
                queue += queueItem(99, created, SyncOp.CREATE)
                tasks["just-made"] = created
                return snapshot + created
            }
        }

        engine(FakeRemote(), local).runSync(false)

        assertEquals(created, local.tasks["just-made"])
    }

    @Test
    fun `a sign-out during the backfill copies no placement`() = runTest {
        val savedAt = now.minusSeconds(7_200)
        val edited = task("save-1")
        val local = FakeLocal().apply {
            tasks["save-1"] = edited
            savedCursor = PbDates.cursorAfter(now)
            contractVersion = 0
        }
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(
            task("save-1", updatedAt = now.minusSeconds(3_600)).copy(savedAt = savedAt), "other", "user-1",
        )
        remote.endSessionOnListing(onCall = 1, nextOwner = null)

        val result = engine(remote, local).runSync(false)

        assertEquals(edited, local.tasks["save-1"])
        assertEquals("error", result.status)
    }

    @Test
    fun `a sign-out during the pull leaves a tombstone standing`() = runTest {
        val buriedAt = now.minusSeconds(3_600).toEpochMilli()
        val local = FakeLocal().apply { tombstones["trashed-task"] = buriedAt }
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("trashed-task", title = "edited elsewhere"), "other", "user-1")
        remote.endSessionOnListing(onCall = 1, nextOwner = "user-2")

        val result = engine(remote, local).runSync(false)

        assertEquals(buriedAt, local.tombstones["trashed-task"])
        assertTrue(local.removeTombstoneCalls.isEmpty())
        assertTrue(local.tasks.isEmpty())
        assertEquals("error", result.status)
    }

    @Test
    fun `a sign-out before the push leaves the queue untouched and spends no retries`() = runTest {
        val local = FakeLocal()
        val edited = task("queued-edit")
        local.tasks[edited.id] = edited
        local.queue += queueItem(1, edited, SyncOp.UPDATE)
        val remote = FakeRemote()
        remote.endSessionOnListing(onCall = 2, nextOwner = null) // call 1 is the pull, call 2 the push index

        engine(remote, local).runSync(false)

        assertTrue(remote.records.isEmpty())
        assertEquals(1, local.queue.size)
        assertEquals(0, local.queue.single().retryCount)
        assertEquals("pending", local.queue.single().status)
    }

    @Test
    fun `a sign-out before reconciliation deletes nothing locally`() = runTest {
        val local = FakeLocal()
        local.tasks["only-here"] = task("only-here") // absent remotely, no queue row: would be deleted
        val remote = FakeRemote()
        remote.endSessionOnListing(onCall = 2, nextOwner = null) // nothing queued: call 2 is reconciliation

        engine(remote, local).runSync(false)

        assertTrue("only-here" in local.tasks)
        assertTrue(local.remoteDeletes.isEmpty())
        assertTrue(local.trashed.isEmpty())
    }

    @Test
    fun `a delete queued before another device's edit is skipped and the edit stays`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val deleted = task("edited-elsewhere", updatedAt = now.minusSeconds(600))
        remote.records["r1"] = WireMapper.push(task("edited-elsewhere", "edited later", now.minusSeconds(30)), "other", "user-1")
        local.tombstones[deleted.id] = now.minusSeconds(60).toEpochMilli()
        local.queue += queueItem(1, deleted, SyncOp.DELETE, queuedAt = now.minusSeconds(60))

        val result = engine(remote, local).runSync(false)

        assertEquals("edited later", remote.records["r1"]?.title)
        assertEquals(0, result.pushed)
        assertTrue(local.queue.isEmpty()) // resolved, not retried
    }

    @Test
    fun `a delete queued after the last remote edit still deletes`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val deleted = task("gone", updatedAt = now.minusSeconds(600))
        remote.records["r1"] = WireMapper.push(deleted, "this-device", "user-1")
        local.tombstones[deleted.id] = now.minusSeconds(60).toEpochMilli()
        local.queue += queueItem(1, deleted, SyncOp.DELETE, queuedAt = now.minusSeconds(60))

        val result = engine(remote, local).runSync(false)

        assertTrue(remote.records.isEmpty())
        assertEquals(1, result.pushed)
    }
}
