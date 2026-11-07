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

// internal (not private): OwnerChangeTest reuses this fake across files in the module.
internal open class FakeLocal : SyncLocalStore {
    val tasks = mutableMapOf<String, Task>()
    val queue = mutableListOf<SyncQueueEntity>()
    val history = mutableListOf<SyncHistoryEntry>()
    var savedCursor: String? = null
    var contractVersion: Int = SyncEngine.PULL_CONTRACT_VERSION
    val remoteDeletes = mutableListOf<String>()
    val tombstones = mutableMapOf<String, Long>()
    val removeTombstoneCalls = mutableListOf<String>()
    val trashed = mutableMapOf<String, Long>()
    val resurrect = mutableSetOf<String>()
    var defaultReminder = 15

    override suspend fun activeTasks(): List<Task> = tasks.values.toList()
    override suspend fun taskById(id: String): Task? = tasks[id]
    override suspend fun upsertFromRemote(task: Task) { tasks[task.id] = task }
    override suspend fun deleteFromRemote(id: String) { tasks.remove(id); remoteDeletes += id }
    override suspend fun abandonToTrash(id: String, deletedAtMs: Long) {
        tasks.remove(id); trashed[id] = deletedAtMs; queue.removeAll { it.taskId == id }
    }
    override suspend fun pendingQueue(): List<SyncQueueEntity> = queue.filter { it.status == "pending" }
    override suspend fun failedQueue(): List<SyncQueueEntity> = queue.filter { it.status == "failed" }
    override suspend fun markQueueItemDone(id: Long) { queue.removeAll { it.id == id } }
    override suspend fun bumpQueueRetry(id: Long, error: String, attemptAtMs: Long, nowFailed: Boolean) {
        val index = queue.indexOfFirst { it.id == id }
        if (index >= 0) {
            queue[index] = queue[index].copy(
                retryCount = queue[index].retryCount + 1,
                lastError = error,
                lastAttemptAt = attemptAtMs,
                status = if (nowFailed) "failed" else "pending",
            )
        }
    }
    override suspend fun requeueFailed() {
        queue.replaceAll { if (it.status == "failed") it.copy(status = "pending") else it }
    }
    override suspend fun cursor(): String? = savedCursor
    override suspend fun saveCursor(cursor: String) { savedCursor = cursor }
    override suspend fun clearCursor() { savedCursor = null }
    override suspend fun pullContractVersion(): Int = contractVersion
    override suspend fun savePullContractVersion(version: Int) { contractVersion = version }
    override suspend fun enqueueUpdate(task: Task, nowMs: Long) {
        queue += SyncQueueEntity(
            id = (queue.maxOfOrNull { it.id } ?: 0L) + 1, taskId = task.id, operation = SyncOp.UPDATE,
            timestamp = nowMs, payloadJson = TaskJson.encode(task),
        )
    }
    override suspend fun deviceId(): String = "this-device"
    override suspend fun insertHistory(entry: SyncHistoryEntry) { history += entry }
    override suspend fun tombstoneAtMs(id: String): Long? = tombstones[id]
    override suspend fun removeTombstone(id: String) {
        tombstones.remove(id)
        removeTombstoneCalls += id
    }
    override suspend fun resurrectOnSight(): Set<String> = resurrect.toSet()
    override suspend fun rememberResurrectOnSight(ids: Set<String>) { resurrect += ids }
    override suspend fun forgetResurrectOnSight(ids: Set<String>) { resurrect -= ids }
    override suspend fun defaultReminderMinutes(): Int = defaultReminder
    override suspend fun settleNotifyBefore(id: String, minutes: Int) {
        tasks[id]?.takeIf { it.notifyBefore == null }?.let { tasks[id] = it.copy(notifyBefore = minutes) }
    }
}

// internal (not private): AccountDeleterTest reuses this fake across files in the module.
internal class FakeRemote : SyncRemote {
    val records = mutableMapOf<String, WireTask>() // recordId -> wire
    var listResponder: ((Int, String) -> TaskListPage)? = null
    var failOnTaskIds = mutableSetOf<String>()
    var rateLimitAfter = Int.MAX_VALUE
    private var writes = 0
    var nextRecordId = 1
    /** True models a dead session: the server answers the identity check as it would a guest. */
    var rejectIdentity = false
    var listCalls = 0
    /** Every list, create, and update in order, so a test can check that a pull came before a push. */
    val calls = mutableListOf<String>()
    /** Runs while the identity check waits on the network: what the user does in that window. */
    var duringIdentityCheck: suspend () -> Unit = {}

    override suspend fun confirmIdentity(ownerId: String) {
        if (rejectIdentity) throw PocketBaseApi.Unauthorized(401)
        duringIdentityCheck()
    }

    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        listCalls++
        calls += "list"
        listResponder?.let { return it(page, updatedCursor) }
        val items = records.entries.sortedBy { it.key }
        return TaskListPage(
            page = 1, totalPages = 1,
            items = items.map { it.value.copy(id = it.key) },
            updatedStamps = items.map { PbDates.formatSpace(PbDates.parse(it.value.clientUpdatedAt) ?: Instant.EPOCH) },
        )
    }

    private fun gate(taskId: String) {
        if (++writes > rateLimitAfter) throw PocketBaseApi.RateLimited()
        if (taskId in failOnTaskIds) throw PocketBaseApi.HttpError(500, "boom")
    }

    override suspend fun createTask(wire: WireTask): WireTask {
        gate(wire.taskId)
        calls += "create"
        val recordId = "rec${nextRecordId++}"
        val stored = wire.copy(id = recordId)
        records[recordId] = stored
        return stored
    }

    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask {
        gate(wire.taskId)
        calls += "update"
        records[recordId] = wire.copy(id = recordId)
        return records[recordId]!!
    }

    override suspend fun deleteTask(recordId: String) {
        gate(records[recordId]?.taskId ?: "")
        records.remove(recordId)
    }
}

class SyncEngineTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun task(id: String, title: String = "t", updatedAt: Instant = now) =
        Task(id = id, title = title, urgent = false, important = false,
            createdAt = now.minusSeconds(600), updatedAt = updatedAt)

    private fun queueItem(
        id: Long,
        task: Task,
        op: SyncOp = SyncOp.UPDATE,
        retryCount: Int = 0,
        lastAttemptAt: Long? = null,
        status: String = "pending",
    ) = SyncQueueEntity(
        id = id, taskId = task.id, operation = op, timestamp = now.toEpochMilli(),
        retryCount = retryCount, payloadJson = TaskJson.encode(task), status = status,
        lastAttemptAt = lastAttemptAt,
    )

    private fun engine(remote: FakeRemote, local: FakeLocal) =
        SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

    private fun engineAt(remote: FakeRemote, local: FakeLocal, epochSeconds: Long) =
        SyncEngine(remote, local, Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC), throttleMs = 0) { "owner" }

    @Test
    fun `absent remotely with a pending row is protected`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "p", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        local.tasks[t.id] = t
        local.queue += SyncQueueEntity(id = 1, taskId = t.id, operation = SyncOp.UPDATE, timestamp = 0, payloadJson = TaskJson.encode(t), retryCount = 1, lastAttemptAt = 0)
        remote.failOnTaskIds += t.id   // push fails, row stays pending
        engineAt(remote, local, 1).runSync(false)
        assertTrue(t.id in local.tasks); assertTrue(local.trashed.isEmpty())
    }

    @Test
    fun `absent remotely with only failed rows is abandoned to trash and its queue rows removed`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "f", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        local.tasks[t.id] = t
        local.queue += SyncQueueEntity(id = 1, taskId = t.id, operation = SyncOp.CREATE, timestamp = 0, payloadJson = TaskJson.encode(t), status = "failed")
        engineAt(remote, local, 123).runSync(false)
        assertNull(local.tasks[t.id])
        assertEquals(123_000L, local.trashed[t.id])
        assertTrue(local.queue.none { it.taskId == t.id })
        assertTrue(local.remoteDeletes.isEmpty())   // not the APPLY path
    }

    @Test
    fun `absent remotely with no rows is deleted locally without a trash entry`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "gone", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        local.tasks[t.id] = t
        engineAt(remote, local, 1).runSync(false)
        assertEquals(listOf(t.id), local.remoteDeletes); assertTrue(local.trashed.isEmpty())
    }

    @Test
    fun `a run with no signed-in owner deletes nothing and reports an error`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote() // an empty remote is what a guest listing looks like
        local.tasks["synced"] = task("synced")             // no queue rows: it already reached the server
        val result = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { null }).runSync(false)
        assertEquals(listOf("synced"), local.tasks.keys.toList())
        assertTrue(local.remoteDeletes.isEmpty())
        assertEquals("error", result.status)
        assertEquals("Not signed in", result.error)
        assertTrue(result.notSignedIn)
    }

    @Test
    fun `an unconfirmed session deletes nothing even when the remote listing is empty`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        remote.rejectIdentity = true            // a dead token: the server lists as a guest, so zero items
        local.tasks["synced"] = task("synced")  // no queue rows: it already reached the server
        val result = engine(remote, local).runSync(false)
        assertEquals(listOf("synced"), local.tasks.keys.toList())
        assertTrue(local.remoteDeletes.isEmpty()); assertTrue(local.trashed.isEmpty())
        assertEquals("error", result.status)
    }

    /**
     * "Worth a look" in the 2026-09-20 review: every run listed the whole collection twice. The push's
     * index is for the queue, so a run with nothing queued lists it once, for reconciliation (§9.5).
     */
    @Test
    fun `a run with nothing queued lists the whole collection once`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        local.savedCursor = "2026-07-19 11:00:00.000Z" // so the pull's listing is not a full one
        local.tasks["synced"] = task("synced")
        remote.createTask(WireMapper.push(task("synced"), "other-device", "user-1", defaultReminder = 15))
        var fullListings = 0
        remote.listResponder = { _, cursor ->
            if (cursor == "1970-01-01 00:00:00.000Z") fullListings++
            val items = remote.records.entries.map { it.value.copy(id = it.key) }
            TaskListPage(page = 1, totalPages = 1, items = items, updatedStamps = items.map { PbDates.formatSpace(now) })
        }

        engine(remote, local).runSync(false)

        assertEquals(1, fullListings)
    }

    // --- D-10: the wire always carries a number (web `tasks/spec-notify-before.md`, sections 2 and 3) ---

    @Test
    fun `a queued null payload pushes a number and stores it on the row, stamp unchanged`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        local.defaultReminder = 30
        val legacy = task("legacy-offset", updatedAt = now.minusSeconds(60)) // queued before the upgrade, offset null
        local.tasks[legacy.id] = legacy
        local.queue += queueItem(1, legacy)

        engine(remote, local).runSync(false)

        assertEquals(30, remote.records.values.single().notifyBefore)
        assertEquals(30, local.tasks[legacy.id]?.notifyBefore)
        assertEquals(legacy.updatedAt, local.tasks[legacy.id]?.updatedAt)
        assertTrue(local.queue.isEmpty())
    }

    /**
     * Rule 2: `payload ?? current row ?? this device's default`. Only a null payload over a null row settles
     * the row, so this device and the server agree without a restamp.
     */
    @Test
    fun `the drain sends the payload's offset, else the row's, else this device's default`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        local.defaultReminder = 30
        data class Case(val id: String, val payload: Int?, val row: Int?, val rowGone: Boolean, val wire: Int, val after: Int?)
        val cases = listOf(
            Case("both-numbers", payload = 20, row = 45, rowGone = false, wire = 20, after = 45),
            Case("payload-null", payload = null, row = 45, rowGone = false, wire = 45, after = 45),
            Case("both-null", payload = null, row = null, rowGone = false, wire = 30, after = 30),
            Case("row-null", payload = 20, row = null, rowGone = false, wire = 20, after = null),
            Case("row-gone", payload = null, row = null, rowGone = true, wire = 30, after = null),
        )
        cases.forEachIndexed { index, case ->
            val payload = task(case.id, updatedAt = now.minusSeconds(60)).copy(notifyBefore = case.payload)
            if (!case.rowGone) local.tasks[case.id] = payload.copy(notifyBefore = case.row)
            local.queue += queueItem(index + 1L, payload)
        }

        engine(remote, local).runSync(false)

        val pushed = remote.records.values.associateBy { it.taskId }
        cases.forEach { case ->
            assertEquals(case.wire, pushed[case.id]?.notifyBefore, "${case.id} on the wire")
            assertEquals(case.after, local.tasks[case.id]?.notifyBefore, "${case.id} on the row")
        }
    }

    @Test
    fun `a run with something queued still takes the index for its push`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        local.savedCursor = "2026-07-19 11:00:00.000Z"
        val edited = task("edited")
        local.tasks["edited"] = edited
        local.queue += queueItem(1, edited, SyncOp.CREATE)
        var fullListings = 0
        remote.listResponder = { _, cursor ->
            if (cursor == "1970-01-01 00:00:00.000Z") fullListings++
            val items = remote.records.entries.map { it.value.copy(id = it.key) }
            TaskListPage(page = 1, totalPages = 1, items = items, updatedStamps = items.map { PbDates.formatSpace(now) })
        }

        engine(remote, local).runSync(false)

        assertEquals(2, fullListings) // one for the push, and a fresh one for reconciliation
        assertEquals(1, remote.records.size)
    }

    /**
     * Review follow-up S1: a token the server rejects is a dead session, even with more than 7 days
     * before it expires, so the run says so and the coordinator signs the user out.
     */
    @Test
    fun `a rejected identity check ends the session`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        remote.rejectIdentity = true
        local.tasks["synced"] = task("synced")
        val result = engine(remote, local).runSync(false)
        assertTrue(result.notSignedIn)
    }

    @Test
    fun `an unconfirmed session abandons nothing to trash`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        remote.rejectIdentity = true
        val stuck = task("stuck")
        local.tasks["stuck"] = stuck
        local.queue += queueItem(1, stuck, SyncOp.CREATE, retryCount = 5, status = "failed")
        val result = engine(remote, local).runSync(false)
        assertEquals(listOf("stuck"), local.tasks.keys.toList())
        assertTrue(local.trashed.isEmpty())
        assertEquals(listOf(1L), local.queue.map { it.id }) // the failed row still shields the task
        assertEquals("error", result.status)
    }

    @Test
    fun `a run with nothing to reconcile never needs the identity check`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        remote.rejectIdentity = true
        remote.records["r1"] = WireMapper.push(task("both-sides"), "other", "user-1", defaultReminder = 15)
        local.tasks["both-sides"] = task("both-sides")
        val result = engine(remote, local).runSync(false)
        assertEquals("success", result.status) // no destructive verdict, so no extra request and no failure
    }

    @Test
    fun `pull applies remote-newer, no-ops equal, keeps local-newer`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        local.tasks["stale"] = task("stale", title = "old local", updatedAt = now.minusSeconds(100))
        local.tasks["equal"] = task("equal", title = "same", updatedAt = now.minusSeconds(50))
        local.tasks["newer"] = task("newer", title = "local wins", updatedAt = now)
        remote.records["r1"] = WireMapper.push(task("stale", "remote wins", now.minusSeconds(10)), "other", "user-1", defaultReminder = 15)
        remote.records["r2"] = WireMapper.push(task("equal", "remote same", now.minusSeconds(50)), "other", "user-1", defaultReminder = 15)
        remote.records["r3"] = WireMapper.push(task("newer", "remote older", now.minusSeconds(500)), "other", "user-1", defaultReminder = 15)
        remote.records["r4"] = WireMapper.push(task("brand-new", "hello", now.minusSeconds(5)), "other", "user-1", defaultReminder = 15)

        val result = engine(remote, local).runSync(triggeredByUser = false)
        assertEquals("remote wins", local.tasks["stale"]!!.title)
        assertEquals("same", local.tasks["equal"]!!.title)      // equal ms → no-op
        assertEquals("local wins", local.tasks["newer"]!!.title) // local newer kept
        assertEquals("hello", local.tasks["brand-new"]!!.title)
        assertEquals(2, result.pulled) // stale + brand-new
        assertEquals(1, result.conflicts) // only stale had a losing local copy
        assertEquals("success", result.status)
    }

    @Test
    fun `cursor advances to max applied minus five seconds`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("aaaa", updatedAt = now.minusSeconds(30)), "other", "user-1", defaultReminder = 15)
        engine(remote, local).runSync(false)
        assertEquals(PbDates.cursorAfter(now.minusSeconds(30)), local.savedCursor)
    }

    @Test
    fun `unparseable updated stamp applies but does not advance cursor`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.listResponder = { page, _ ->
            TaskListPage(
                page = 1, totalPages = 1,
                items = listOf(WireMapper.push(task("weird"), "other", "user-1", defaultReminder = 15)),
                updatedStamps = listOf("not-a-date"),
            )
        }
        engine(remote, local).runSync(false)
        assertEquals("weird", local.tasks["weird"]!!.id)
        assertNull(local.savedCursor)
    }

    @Test
    fun `pull drops a record whose tombstone is not strictly older`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        local.tombstones["task1"] = now.toEpochMilli() // archived/trashed at exactly "now"
        remote.records["r1"] = WireMapper.push(task("task1", "resurrected?", now), "other", "user-1", defaultReminder = 15) // equal instant

        engine(remote, local).runSync(false)

        assertTrue("task1" !in local.tasks) // suppressed, never applied
        assertEquals(now.toEpochMilli(), local.tombstones["task1"]) // tombstone stands
        assertTrue(local.removeTombstoneCalls.isEmpty())
    }

    @Test
    fun `pull resurrects when the remote edit is strictly newer than the tombstone`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        local.tombstones["task1"] = now.minusSeconds(10).toEpochMilli()
        remote.records["r1"] = WireMapper.push(task("task1", "edited elsewhere", now), "other", "user-1", defaultReminder = 15)

        engine(remote, local).runSync(false)

        assertEquals("edited elsewhere", local.tasks["task1"]!!.title)
        assertEquals(listOf("task1"), local.removeTombstoneCalls) // removed exactly once
        assertTrue("task1" !in local.tombstones)
    }

    @Test
    fun `unparseable remote timestamp never resurrects`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        local.tombstones["task1"] = now.toEpochMilli()
        remote.listResponder = { _, _ ->
            TaskListPage(
                page = 1, totalPages = 1,
                items = listOf(WireMapper.push(task("task1"), "other", "user-1", defaultReminder = 15).copy(clientUpdatedAt = "garbage")),
                updatedStamps = listOf("garbage"),
            )
        }

        engine(remote, local).runSync(false)

        assertTrue("task1" !in local.tasks)
        assertEquals(now.toEpochMilli(), local.tombstones["task1"]) // untouched, tombstone stands
    }

    @Test
    fun `push creates missing, updates existing, and delete uses the live index`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val fresh = task("fresh-task", updatedAt = now)
        local.tasks["fresh-task"] = fresh
        local.queue += queueItem(1, fresh, SyncOp.CREATE)
        local.queue += queueItem(2, fresh, SyncOp.DELETE).copy(payloadJson = null)

        val result = engine(remote, local).runSync(false)
        // create then delete in the same drain: the delete found the fresh record id
        assertEquals(0, remote.records.size)
        assertEquals(2, result.pushed)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `push payload guard skips items older than remote`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["r9"] = WireMapper.push(task("guarded", "remote newer", now), "other", "user-1", defaultReminder = 15)
        val stale = task("guarded", "stale local", now.minusSeconds(500))
        local.queue += queueItem(1, stale, SyncOp.UPDATE)

        val result = engine(remote, local).runSync(false)
        assertEquals("remote newer", remote.records["r9"]!!.title) // untouched
        assertEquals(0, result.pushed)
        assertTrue(local.queue.isEmpty()) // skipped item resolved, not retried forever
    }

    /**
     * Parity backlog B-47: an archive's delete carries the task as archived. Another device's edit can
     * predate the delete yet postdate that version (a pull missed it, or it landed between the pull and
     * the archive). Archiving a stale copy must not delete the newer task.
     */
    @Test
    fun `an archive delete is skipped when the remote was edited after the archived version`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val archived = task("reopened", "done here", now.minusSeconds(500))
        remote.records["r1"] = WireMapper.push(task("reopened", "reopened elsewhere", now.minusSeconds(200)), "other", "user-1", defaultReminder = 15)
        local.tombstones["reopened"] = now.minusSeconds(100).toEpochMilli() // archived after that edit
        local.queue += queueItem(1, archived, SyncOp.DELETE)

        val result = engine(remote, local).runSync(false)

        assertEquals("reopened elsewhere", remote.records["r1"]?.title) // the newer edit survives
        assertEquals(0, result.pushed)
        assertTrue(local.queue.isEmpty()) // resolved, not retried
    }

    @Test
    fun `an archive delete still deletes when the remote holds the archived version`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val archived = task("archived", "done", now.minusSeconds(500))
        remote.records["r1"] = WireMapper.push(archived, "this-device", "user-1", defaultReminder = 15)
        local.tombstones["archived"] = now.minusSeconds(100).toEpochMilli()
        local.queue += queueItem(1, archived, SyncOp.DELETE)

        val result = engine(remote, local).runSync(false)

        assertTrue(remote.records.isEmpty())
        assertEquals(1, result.pushed)
    }

    /** Parity backlog B-46: the auto-archive sweep waits for a clean run, which means its pull completed. */
    @Test
    fun `a run is clean when it finished without an error, push failures included`() = runTest {
        val partialLocal = FakeLocal().apply { queue += queueItem(1, task("doomed"), retryCount = 4, lastAttemptAt = 0L) }
        val failingRemote = FakeRemote().apply { failOnTaskIds += "doomed" }
        val downRemote = FakeRemote().apply { listResponder = { _, _ -> throw PocketBaseApi.HttpError(500, "down") } }

        val success = engine(FakeRemote(), FakeLocal()).runSync(false)
        val partial = engine(failingRemote, partialLocal).runSync(false)
        val pullFailed = engine(downRemote, FakeLocal()).runSync(false)
        val signedOut = SyncEngine(FakeRemote(), FakeLocal(), clock, throttleMs = 0, ownerId = { null }).runSync(false)

        assertTrue(success.clean)
        assertEquals("partial", partial.status)
        assertTrue(partial.clean)
        assertFalse(pullFailed.clean)
        assertFalse(signedOut.clean)
    }

    @Test
    fun `backoff gates retries until due`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val item = task("later")
        // retryCount 2 → 10s backoff; last attempt 3s ago → not due
        local.queue += queueItem(1, item, retryCount = 2, lastAttemptAt = now.minusSeconds(3).toEpochMilli())
        engine(remote, local).runSync(false)
        assertEquals(1, local.queue.size)
        assertEquals(2, local.queue.single().retryCount) // untouched, still pending
    }

    @Test
    fun `fifth failure marks failed and counts in history`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.failOnTaskIds += "doomed"
        local.queue += queueItem(1, task("doomed"), retryCount = 4, lastAttemptAt = 0L)
        val result = engine(remote, local).runSync(false)
        assertEquals("failed", local.queue.single().status)
        assertEquals("partial", result.status)
        assertEquals(1, result.failedItems)
        assertEquals("partial", local.history.single().status)
    }

    @Test
    fun `429 aborts the push loop leaving items pending`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.rateLimitAfter = 1
        local.queue += queueItem(1, task("first-ok"), SyncOp.CREATE)
        local.queue += queueItem(2, task("second-blocked"), SyncOp.CREATE)
        local.queue += queueItem(3, task("third-blocked"), SyncOp.CREATE)
        engine(remote, local).runSync(false)
        assertEquals(1, remote.records.size)
        assertEquals(2, local.queue.count { it.status == "pending" }) // untouched, no retry bumps
        assertEquals(0, local.queue.single { it.taskId == "second-blocked" }.retryCount)
    }

    @Test
    fun `deletion reconciliation removes unprotected absentees only`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("kept-remote"), "other", "user-1", defaultReminder = 15)
        local.tasks["kept-remote"] = task("kept-remote")
        local.tasks["gone-remote"] = task("gone-remote")
        local.tasks["protected-pending"] = task("protected-pending")
        local.tasks["abandoned-failed"] = task("abandoned-failed")
        local.queue += queueItem(1, local.tasks["protected-pending"]!!, SyncOp.CREATE)
        local.queue += queueItem(2, local.tasks["abandoned-failed"]!!, SyncOp.CREATE,
            retryCount = 5, status = "failed")

        engine(remote, local).runSync(false)
        assertEquals(listOf("gone-remote"), local.remoteDeletes) // APPLY: no queue rows at all
        assertTrue("protected-pending" in local.tasks) // PROTECT: pending row
        assertNull(local.tasks["abandoned-failed"]) // ABANDON: only failed rows
        assertTrue("abandoned-failed" in local.trashed)
    }

    @Test
    fun `history records counts, status, and trigger`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("incoming"), "other", "user-1", defaultReminder = 15)
        engine(remote, local).runSync(triggeredByUser = true)
        val entry = local.history.single()
        assertEquals("success", entry.status)
        assertEquals(1, entry.pulledCount)
        assertEquals("user", entry.triggeredBy)
        assertEquals("this-device", entry.deviceId)
    }

    @Test
    fun `remote failure during pull records error status`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.listResponder = { _, _ -> throw PocketBaseApi.HttpError(500, "down") }
        val result = engine(remote, local).runSync(false)
        assertEquals("error", result.status)
        assertEquals("error", local.history.single().status)
    }

    @Test
    fun `an over-cap remote listing aborts the run and deletes nothing locally`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "keep me", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        local.tasks[t.id] = t
        remote.listResponder = { page, _ -> TaskListPage(page = page, totalPages = 1, totalItems = 10_001, items = emptyList(), updatedStamps = emptyList()) }
        val result = SyncEngine(remote, local, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), throttleMs = 0) { "owner" }.runSync(false)
        assertEquals("error", result.status)
        assertTrue(result.error!!.contains("exceeds cap"))
        assertEquals(listOf(t.id), local.tasks.keys.toList())   // reconciliation never ran
        assertTrue(local.remoteDeletes.isEmpty())
    }

    @Test
    fun `a pending update is skipped and its retry bumped when the remote version is unparseable`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = Task(title = "edit", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH.plusSeconds(10))
        local.tasks[t.id] = t
        local.queue += SyncQueueEntity(id = 1, taskId = t.id, operation = SyncOp.UPDATE, timestamp = 0, payloadJson = TaskJson.encode(t))
        remote.records["rec-1"] = WireTask(taskId = t.id, clientUpdatedAt = "not-a-date")
        val result = SyncEngine(remote, local, Clock.fixed(Instant.EPOCH.plusSeconds(60), ZoneOffset.UTC), throttleMs = 0) { "owner" }.runSync(false)
        assertEquals(0, result.pushed)
        assertEquals("not-a-date", remote.records.getValue("rec-1").clientUpdatedAt)   // untouched
        val row = local.queue.single()
        assertEquals(1, row.retryCount)
        assertEquals("Remote task version is missing or malformed", row.lastError)
        assertEquals("pending", row.status)
    }

    @Test
    fun `health precedence`() {
        assertEquals(SyncHealth.OFFLINE, Health.evaluate(true, 0, 1, 5, null))
        assertEquals(SyncHealth.SESSION_EXPIRED, Health.evaluate(false, 1, 2, 5, null))
        assertEquals(SyncHealth.FAILED_ITEMS, Health.evaluate(false, 99, 2, 5, null))
        assertEquals(SyncHealth.STALE_QUEUE, Health.evaluate(false, 99, 2, 0, 2L * 60 * 60 * 1000))
        assertEquals(SyncHealth.OK, Health.evaluate(false, 99, 2, 0, 10_000))
    }

    /**
     * Resurrect on sight (spec §8.21, §9.4): a Trash delete made while signed out was dropped at sign-in
     * and its id remembered. The pull alone cannot bring the task back, because the tombstone is newer
     * than the server copy, and a kept cursor would not even list it. The run lists the account once
     * and does.
     */
    @Test
    fun `a remembered delete comes back when the account lists the task`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val serverCopy = task("deleted-signed-out", title = "remote", updatedAt = now.minusSeconds(3600))
        remote.records["rec1"] = WireMapper.push(serverCopy, "other-device", "user-1", defaultReminder = 15)
        local.tombstones[serverCopy.id] = now.toEpochMilli() // trashed after the server copy's last edit
        local.resurrect += serverCopy.id
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        val result = engine.runSync(triggeredByUser = false)

        assertEquals("remote", local.tasks[serverCopy.id]?.title)
        assertEquals(listOf(serverCopy.id), local.removeTombstoneCalls)
        assertTrue(local.resurrect.isEmpty())
        assertEquals(1, result.pulled)
    }

    /** No server copy means the Trash row is the only copy, so it stays. The id is still forgotten. */
    @Test
    fun `a remembered delete with no server copy keeps its Trash copy and is forgotten`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        local.tombstones["never-synced"] = now.toEpochMilli()
        local.resurrect += "never-synced"
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertNull(local.tasks["never-synced"])
        assertEquals(now.toEpochMilli(), local.tombstones["never-synced"])
        assertTrue(local.resurrect.isEmpty())
    }

    /** The listing an ended session gets is a guest's empty one, which proves nothing (spec §9.4). */
    @Test
    fun `a dead session forgets no remembered delete`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote().apply { rejectIdentity = true }
        local.tombstones["t1"] = now.toEpochMilli()
        local.resurrect += "t1"
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        val result = engine.runSync(triggeredByUser = false)

        assertTrue(result.notSignedIn)
        assertEquals(setOf("t1"), local.resurrect)
        assertEquals(now.toEpochMilli(), local.tombstones["t1"])
    }

    /**
     * Restored from Trash and edited before the sign-in: last-write-wins still decides. The id was `t1` until
     * 2026-11-07, which `WireMapper.pull` rejects as too short, so the record never reached the rule this pins.
     */
    @Test
    fun `a remembered id active again with a newer local edit keeps the local copy`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["rec1"] = WireMapper.push(task("restored-task", title = "remote", updatedAt = now.minusSeconds(3600)), "other-device", "user-1", defaultReminder = 15)
        local.tasks["restored-task"] = task("restored-task", title = "local", updatedAt = now)
        local.resurrect += "restored-task"
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertEquals("local", local.tasks["restored-task"]?.title)
        assertTrue(local.resurrect.isEmpty())
    }

    /** Guards the step's early return. It passed before the step existed; the mutation note is in the PR. */
    @Test
    fun `nothing remembered means no extra listing`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["rec1"] = WireMapper.push(task("t1"), "other-device", "user-1", defaultReminder = 15)
        local.tasks["t1"] = task("t1")
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertEquals(2, remote.listCalls) // the pull and the reconciliation index
    }

    /**
     * SPEC-3 rule 4, as amended from S-AND's review of #680 (finding 2). Signed out, the user edited a task and then
     * trashed it, so its UPDATE outlived the sign-in that dropped the DELETE. The server stamp is far-future, so the
     * hold-off skips the id. Forgetting it stranded the Trash row here while the server copy, healed by this run's
     * push, stayed active everywhere else. It stays remembered, and the next run brings the task back.
     */
    @Test
    fun `a remembered id the hold-off skips stays for the next run`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val edited = task("trashed-task", title = "edited signed out", updatedAt = now.minusSeconds(120))
        remote.records["rec1"] = WireMapper.push(
            task("trashed-task", title = "fast clock", updatedAt = Instant.parse("2099-01-01T00:00:00.000Z")), "other-device", "user-1", defaultReminder = 15,
        )
        local.tombstones["trashed-task"] = now.minusSeconds(60).toEpochMilli() // trashed after the edit
        local.queue += queueItem(1, edited)
        local.resurrect += "trashed-task"
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertEquals(setOf("trashed-task"), local.resurrect) // skipped, so not forgotten
        assertEquals("edited signed out", remote.records.getValue("rec1").title) // the push healed the record

        engine.runSync(triggeredByUser = false)

        assertEquals("edited signed out", local.tasks["trashed-task"]?.title) // out of Trash, as the server has it
        assertTrue(local.resurrect.isEmpty())
    }

    /** SPEC-3 rule 5. Passed before it was written: `BoundedListing` throws first. The PR has the mutation. */
    @Test
    fun `an incomplete listing forgets no remembered delete`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        local.savedCursor = "2026-07-19 11:00:00.000Z" // so only the step lists from the epoch
        local.tombstones["t1"] = now.toEpochMilli()
        local.resurrect += "t1"
        remote.listResponder = { _, cursor ->
            if (cursor == "1970-01-01 00:00:00.000Z") {
                val item = WireMapper.push(task("t1"), "other-device", "user-1", defaultReminder = 15).copy(id = "rec1")
                TaskListPage(page = 1, totalPages = 1, totalItems = 2, items = listOf(item), updatedStamps = listOf(PbDates.formatSpace(now)))
            } else {
                TaskListPage(page = 1, totalPages = 1, items = emptyList(), updatedStamps = emptyList())
            }
        }
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        val result = engine.runSync(triggeredByUser = false)

        assertEquals("error", result.status)
        assertEquals(setOf("t1"), local.resurrect)
        assertEquals(now.toEpochMilli(), local.tombstones["t1"])
    }

    /**
     * SPEC-3 §6. Only sign-in drops a queued delete (`SyncCoordinator.confirmOwner`), and it isn't keyed on the cursor,
     * so a first sync that fails leaves a delete made after sign-in queued, and the next run sends it. Passed before it
     * was written; the PR has the mutation.
     */
    @Test
    fun `a failed first sync doesn't drop a delete made while signed in`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["rec1"] = WireMapper.push(task("t1"), "other-device", "user-1", defaultReminder = 15)
        local.queue += SyncQueueEntity.tombstone("t1", now).copy(id = 1)
        remote.listResponder = { _, _ -> throw PocketBaseApi.HttpError(500, "boom") }
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        assertEquals("error", engine.runSync(triggeredByUser = false).status)
        assertEquals(listOf(SyncOp.DELETE), local.queue.map { it.operation })
        assertTrue(local.resurrect.isEmpty())

        remote.listResponder = null
        engine.runSync(triggeredByUser = false)

        assertTrue(remote.records.isEmpty())
    }

    /** SPEC-3 rule 7. Passed before it was written; the PR has the mutation. */
    @Test
    fun `a delete made while signed in still deletes on the server`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["rec1"] = WireMapper.push(task("t1"), "other-device", "user-1", defaultReminder = 15)
        local.queue += SyncQueueEntity.tombstone("t1", now).copy(id = 1)
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertTrue(remote.records.isEmpty())
        assertTrue(local.queue.isEmpty())
        assertTrue(local.resurrect.isEmpty())
    }

    /**
     * Sprint backlog RECONCILE-RECHECK: the verdicts are computed, then the identity check waits on the
     * network. An edit queued during that wait protects its task, so the queue is read again before any
     * verdict runs. Here the task had no queue row, so the stale verdict was APPLY: delete it locally.
     */
    @Test
    fun `an edit queued during the identity check protects its task from the delete verdict`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val edited = task("edited-during-check")
        local.tasks[edited.id] = edited // absent remotely, no queue row
        remote.duringIdentityCheck = { local.enqueueUpdate(edited, now.toEpochMilli()) }
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertEquals(edited, local.tasks[edited.id])
        assertTrue(local.remoteDeletes.isEmpty())
    }

    /** The same window, for a task whose only row had failed: the stale verdict was ABANDON to trash. */
    @Test
    fun `an edit queued during the identity check protects its task from the trash verdict`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val edited = task("edited-during-check")
        local.tasks[edited.id] = edited
        local.queue += queueItem(1, edited, retryCount = 5, status = "failed")
        remote.duringIdentityCheck = { local.enqueueUpdate(edited, now.toEpochMilli()) }
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertEquals(edited, local.tasks[edited.id])
        assertTrue(local.trashed.isEmpty())
    }
}
