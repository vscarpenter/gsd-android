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
 * Backlog B-44 and sprint question S-AND-02. Most rules mirror the web. The queued-write rule is native
 * only: the web pushes before it pulls, so its edit has healed the server by then. Android pulls first,
 * so without the rule the capped pull would overwrite the edit it's about to push.
 */
class FarFutureStampTest {
    private val now = Instant.parse("2026-10-31T15:00:00.000Z")
    private val ceiling = now.plusSeconds(300)
    private val farFuture = Instant.parse("2099-01-01T00:00:00.000Z")
    private val insideWindow = now.plusSeconds(120)
    private val longAgo = now.minusSeconds(86_400)
    private val edited = now.minusSeconds(60)
    private val savedAt = now.minusSeconds(7_200)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun task(title: String = "local", updatedAt: Instant = longAgo, savedAt: Instant? = null) =
        Task(id = "task-1", title = title, urgent = false, important = true,
            createdAt = longAgo.minusSeconds(600), updatedAt = updatedAt, savedAt = savedAt)

    private fun engine(remote: FakeRemote, local: FakeLocal) =
        SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

    /** A server record with a poisoned client stamp. The server's own `updated`, which drives the cursor, stays real. */
    private fun serverWith(stamp: Instant, title: String = "remote", savedAt: Instant? = null) = FakeRemote().apply {
        records["r1"] = WireMapper.push(task(title, stamp, savedAt), "other-device", "user-1")
        listResponder = { _, _ ->
            val items = records.entries.sortedBy { it.key }.map { it.value.copy(id = it.key) }
            TaskListPage(page = 1, totalPages = 1, items = items, updatedStamps = items.map { PbDates.formatSpace(now) })
        }
    }

    private fun remoteTitle(remote: FakeRemote) = remote.records.getValue("r1").title
    private fun remoteStamp(remote: FakeRemote) = remote.records.getValue("r1").clientUpdatedAt

    private fun queued(row: Task, op: SyncOp = SyncOp.UPDATE, status: String = "pending", at: Instant = edited) =
        SyncQueueEntity(id = 1, taskId = row.id, operation = op, timestamp = at.toEpochMilli(),
            payloadJson = TaskJson.encode(row), status = status)

    private fun trashDelete(at: Instant = edited) =
        SyncQueueEntity(id = 1, taskId = "task-1", operation = SyncOp.DELETE, timestamp = at.toEpochMilli(), payloadJson = null)

    private fun localWithQueuedEdit(status: String = "pending"): Pair<FakeLocal, Task> {
        val edit = task("my edit", edited)
        return FakeLocal().apply { tasks["task-1"] = edit; queue += queued(edit, status = status) } to edit
    }

    // The helper: same constant and boundary as the web

    @Test
    fun `a stamp at the ceiling is left alone`() {
        assertFalse(RemoteStamp.isPastCeiling(ceiling, now))
        assertEquals(ceiling, RemoteStamp.capped(ceiling, now))
    }

    @Test
    fun `a stamp one millisecond past the ceiling comes down to it`() {
        val past = ceiling.plusMillis(1)
        assertTrue(RemoteStamp.isPastCeiling(past, now))
        assertEquals(ceiling, RemoteStamp.capped(past, now))
    }

    @Test
    fun `a stamp at the ceiling can be newer, and one past it never is`() {
        assertTrue(RemoteStamp.isNewer(ceiling, now.toEpochMilli(), now))
        assertFalse(RemoteStamp.isNewer(ceiling.plusMillis(1), now.toEpochMilli(), now))
    }

    @Test
    fun `an older or equal stamp isn't newer`() {
        assertFalse(RemoteStamp.isNewer(now, now.toEpochMilli(), now))
        assertFalse(RemoteStamp.isNewer(longAgo, now.toEpochMilli(), now))
    }

    // Pull with no queued write

    @Test
    fun `a new far-future record lands at the ceiling`() = runTest {
        val local = FakeLocal()

        engine(serverWith(farFuture), local).runSync(false)

        assertEquals("remote", local.tasks["task-1"]!!.title)
        assertEquals(ceiling, local.tasks["task-1"]!!.updatedAt)
    }

    @Test
    fun `a far-future record with nothing queued applies over a live row at the ceiling`() = runTest {
        val local = FakeLocal().apply { tasks["task-1"] = task() }

        val result = engine(serverWith(farFuture), local).runSync(false)

        assertEquals("remote", local.tasks["task-1"]!!.title)
        assertEquals(ceiling, local.tasks["task-1"]!!.updatedAt)
        assertEquals(1, result.conflicts)
    }

    @Test
    fun `a stamp inside the window stays uncapped`() = runTest {
        val local = FakeLocal().apply { tasks["task-1"] = task() }

        engine(serverWith(insideWindow), local).runSync(false)

        assertEquals("remote", local.tasks["task-1"]!!.title)
        assertEquals(insideWindow, local.tasks["task-1"]!!.updatedAt)
    }

    @Test
    fun `a row already at the ceiling is a no-op against a far-future remote`() = runTest {
        val atCeiling = task(updatedAt = ceiling)
        val local = FakeLocal().apply { tasks["task-1"] = atCeiling }

        val result = engine(serverWith(farFuture), local).runSync(false)

        assertEquals(atCeiling, local.tasks["task-1"])
        assertEquals(0, result.conflicts)
    }

    // The queued-write rule, in the real order of pull then push (S-AND-02)

    @Test
    fun `a queued edit survives a far-future remote, reaches the server, and the next sync is a no-op`() = runTest {
        val (local, edit) = localWithQueuedEdit()
        val remote = serverWith(farFuture)

        val first = engine(remote, local).runSync(false)

        assertEquals(edit, local.tasks["task-1"])
        assertEquals("my edit", remoteTitle(remote))
        assertEquals(PbDates.formatWire(edited), remoteStamp(remote))
        assertEquals(0, first.conflicts)
        assertTrue(local.queue.isEmpty())

        val second = engine(remote, local).runSync(false)

        assertEquals(edit, local.tasks["task-1"])
        assertEquals(0, second.pulled)
    }

    @Test
    fun `a failed row keeps its edit against a far-future remote, and a revive heals the server`() = runTest {
        val (local, edit) = localWithQueuedEdit(status = "failed")
        val remote = serverWith(farFuture)

        engine(remote, local).runSync(false)

        assertEquals(edit, local.tasks["task-1"])
        assertEquals("remote", remoteTitle(remote)) // a failed row isn't pushed this run

        local.requeueFailed()
        engine(remote, local).runSync(false)

        assertEquals(edit, local.tasks["task-1"])
        assertEquals("my edit", remoteTitle(remote))
    }

    @Test
    fun `a queued edit against a stamp inside the window follows the old rule`() = runTest {
        val (local, _) = localWithQueuedEdit()
        val remote = serverWith(insideWindow)

        engine(remote, local).runSync(false)

        assertEquals("remote", local.tasks["task-1"]!!.title)
        assertEquals("remote", remoteTitle(remote)) // the guard dropped the older edit
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a record the rule skips doesn't advance the cursor`() = runTest {
        val (local, _) = localWithQueuedEdit(status = "failed")
        val stored = PbDates.cursorAfter(longAgo)
        local.savedCursor = stored

        engine(serverWith(farFuture), local).runSync(false)

        assertEquals(stored, local.savedCursor)
    }

    // Push and stale deletes

    @Test
    fun `a far-future remote doesn't make a trash delete stale`() = runTest {
        val local = FakeLocal().apply { queue += trashDelete(); tombstones["task-1"] = edited.toEpochMilli() }
        val remote = serverWith(farFuture)

        engine(remote, local).runSync(false)

        assertTrue(remote.records.isEmpty())
    }

    @Test
    fun `a far-future remote doesn't make an archive's delete stale`() = runTest {
        val archived = task(updatedAt = edited)
        val local = FakeLocal().apply {
            queue += queued(archived, SyncOp.DELETE)
            tombstones["task-1"] = edited.toEpochMilli()
        }
        val remote = serverWith(farFuture)

        engine(remote, local).runSync(false)

        assertTrue(remote.records.isEmpty())
    }

    @Test
    fun `a stamp inside the window still makes a delete stale`() = runTest {
        // The pull resurrects the row, as a strictly newer edit does: the tombstone is older than the stamp.
        val local = FakeLocal().apply { queue += trashDelete(); tombstones["task-1"] = edited.toEpochMilli() }
        val remote = serverWith(insideWindow)

        engine(remote, local).runSync(false)

        assertEquals("remote", remoteTitle(remote))
    }

    // Tombstones

    @Test
    fun `a far-future stamp leaves a tombstone standing on pull`() = runTest {
        val local = FakeLocal().apply { tombstones["task-1"] = edited.toEpochMilli() }

        engine(serverWith(farFuture), local).runSync(false)

        assertNull(local.tasks["task-1"])
        assertEquals(edited.toEpochMilli(), local.tombstones["task-1"])
    }

    @Test
    fun `a far-future stamp leaves a tombstone standing on realtime`() = runTest {
        val local = FakeLocal().apply { tombstones["task-1"] = edited.toEpochMilli() }

        applyRealtimeUpsert(local, WireMapper.push(task("remote", farFuture), "other-device", "user-1"), now) { true }

        assertNull(local.tasks["task-1"])
        assertEquals(edited.toEpochMilli(), local.tombstones["task-1"])
    }

    @Test
    fun `a newer stamp inside the window still resurrects`() = runTest {
        val local = FakeLocal().apply { tombstones["task-1"] = edited.toEpochMilli() }

        engine(serverWith(insideWindow), local).runSync(false)

        assertEquals("remote", local.tasks["task-1"]!!.title)
        assertNull(local.tombstones["task-1"])
    }

    // Realtime

    private fun realtime(stamp: Instant) = WireMapper.push(task("remote", stamp), "other-device", "user-1")

    @Test
    fun `realtime stores a new far-future record at the ceiling`() = runTest {
        val local = FakeLocal()

        applyRealtimeUpsert(local, realtime(farFuture), now) { true }

        assertEquals(ceiling, local.tasks["task-1"]!!.updatedAt)
    }

    @Test
    fun `realtime applies a far-future record over a live row at the ceiling`() = runTest {
        val local = FakeLocal().apply { tasks["task-1"] = task() }

        applyRealtimeUpsert(local, realtime(farFuture), now) { true }

        assertEquals("remote", local.tasks["task-1"]!!.title)
        assertEquals(ceiling, local.tasks["task-1"]!!.updatedAt)
    }

    @Test
    fun `realtime leaves a row with a queued edit alone against a far-future stamp`() = runTest {
        val (pending, pendingEdit) = localWithQueuedEdit()
        val (failed, failedEdit) = localWithQueuedEdit(status = "failed")

        applyRealtimeUpsert(pending, realtime(farFuture), now) { true }
        applyRealtimeUpsert(failed, realtime(farFuture), now) { true }

        assertEquals(pendingEdit, pending.tasks["task-1"])
        assertEquals(failedEdit, failed.tasks["task-1"])
    }

    @Test
    fun `a queued delete isn't a write the rule protects`() = runTest {
        val local = FakeLocal().apply { tasks["task-1"] = task(); queue += trashDelete() }

        applyRealtimeUpsert(local, realtime(farFuture), now) { true }

        assertEquals("remote", local.tasks["task-1"]!!.title)
    }

    @Test
    fun `realtime keeps today's rule for a queued edit inside the window`() = runTest {
        val (local, _) = localWithQueuedEdit()

        applyRealtimeUpsert(local, realtime(insideWindow), now) { true }

        assertEquals("remote", local.tasks["task-1"]!!.title)
    }

    @Test
    fun `realtime leaves a row at the ceiling alone`() = runTest {
        val atCeiling = task(updatedAt = ceiling)
        val local = FakeLocal().apply { tasks["task-1"] = atCeiling }

        applyRealtimeUpsert(local, realtime(farFuture), now) { true }

        assertEquals(atCeiling, local.tasks["task-1"])
    }

    // How the rule meets the Saved backfill (S-AND-02, AND-44 only)

    private fun FakeLocal.pendingBackfill() = apply {
        savedCursor = PbDates.cursorAfter(now)
        contractVersion = 0
    }

    /** The `savedAt` version of the cross-client test `a far-future remote with a queued edit keeps the server pick through the backfill` (S-SPEC-01). */
    @Test
    fun `the backfill copies placement from a far-future remote onto a row with a queued edit`() = runTest {
        val (local, edit) = localWithQueuedEdit()
        local.pendingBackfill()
        val remote = serverWith(farFuture, savedAt = savedAt)

        val result = engine(remote, local).runSync(false)

        assertEquals(edit.copy(savedAt = savedAt), local.tasks["task-1"])
        assertEquals(PbDates.formatWire(savedAt), remote.records.getValue("r1").savedAt) // the trap: never ""
        assertEquals("my edit", remoteTitle(remote))
        assertEquals(0, result.conflicts)
    }

    /** Sprint questions S-SPEC-01 and S-WEB-02: the far-future hold-off copies placement under the same can-be-Saved guard. */
    @Test
    fun `the backfill copies no placement from a far-future remote onto a completed row with a queued edit`() = runTest {
        val done = task("my edit", edited).copy(completed = true, completedAt = edited)
        val local = FakeLocal().apply { tasks["task-1"] = done; queue += queued(done) }
        local.pendingBackfill()
        val remote = serverWith(farFuture, savedAt = savedAt)

        engine(remote, local).runSync(false)

        assertEquals(done, local.tasks["task-1"])
        assertEquals(true, remote.records.getValue("r1").completed)
        assertEquals("", remote.records.getValue("r1").savedAt) // the newer completion wins on the server too
    }

    @Test
    fun `the backfill keeps a placement this device already has against a far-future remote`() = runTest {
        val savedHere = edited.minusSeconds(5)
        val edit = task("my edit", edited, savedAt = savedHere)
        val local = FakeLocal().apply { tasks["task-1"] = edit; queue += queued(edit) }.pendingBackfill()

        engine(serverWith(farFuture, savedAt = savedAt), local).runSync(false)

        assertEquals(savedHere, local.tasks["task-1"]!!.savedAt)
    }

    @Test
    fun `a far-future Saved remote with nothing queued takes the whole row at the ceiling during the backfill`() = runTest {
        val local = FakeLocal().apply { tasks["task-1"] = task() }.pendingBackfill()

        engine(serverWith(farFuture, savedAt = savedAt), local).runSync(false)

        val row = local.tasks["task-1"]!!
        assertEquals("remote", row.title)
        assertEquals(savedAt, row.savedAt)
        assertEquals(ceiling, row.updatedAt)
    }

    @Test
    fun `an ordinary pull leaves a local return active against a far-future Saved remote`() = runTest {
        val returned = task("returned", edited)
        val local = FakeLocal().apply { tasks["task-1"] = returned; queue += queued(returned) }
        val remote = serverWith(farFuture, savedAt = savedAt)

        engine(remote, local).runSync(false)

        assertNull(local.tasks["task-1"]!!.savedAt)
        assertEquals("", remote.records.getValue("r1").savedAt)
    }
}
