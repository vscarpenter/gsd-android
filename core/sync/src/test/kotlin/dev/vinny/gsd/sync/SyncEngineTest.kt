package dev.vinny.gsd.sync

import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.model.TimeEntry
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
    /** Which tombstoned ids are archive rows rather than Trash rows. */
    val archived = mutableSetOf<String>()
    /** Archive and Trash rows by id, for a task that comes back over its tombstone to merge into. */
    val buried = mutableMapOf<String, Task>()

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
    override suspend fun restampIfUnchanged(id: String, fromMs: Long, toMs: Long): Boolean {
        val row = tasks[id]?.takeIf { it.updatedAt.toEpochMilli() == fromMs } ?: return false
        tasks[id] = row.copy(updatedAt = Instant.ofEpochMilli(toMs))
        return true
    }
    override suspend fun isArchived(id: String): Boolean = id in archived
    override suspend fun buriedTask(id: String): Task? = buried[id]
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
    /** True models a token the server confirms for another account, as `PocketBaseApi.confirmIdentity` reports it. */
    var otherAccount = false
    /** Thrown by every create, update, and delete, as a server that refuses a write models a 401 or 403. */
    var writeFailure: Exception? = null
    var listCalls = 0
    /** Every list, create, and update in order, so a test can check that a pull came before a push. */
    val calls = mutableListOf<String>()
    /** Runs while the identity check waits on the network: what the user does in that window. */
    var duringIdentityCheck: suspend () -> Unit = {}
    /** How many listings from the epoch claim one more record than they send, as a listing that changed mid-walk. */
    var incompleteEpochListings = 0
    /** Runs after a guarded update lands, as the user signing out while the PATCH was in flight. */
    var afterGuardedUpdate: suspend () -> Unit = {}

    override suspend fun confirmIdentity(ownerId: String) {
        calls += "confirmIdentity"
        if (rejectIdentity) throw PocketBaseApi.Unauthorized(401)
        if (otherAccount) throw IdentityMismatch()
        duringIdentityCheck()
    }

    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        listCalls++
        calls += "list"
        listResponder?.let { return it(page, updatedCursor) }
        val items = records.entries.sortedBy { it.key }
        val incomplete = updatedCursor == "1970-01-01 00:00:00.000Z" && incompleteEpochListings > 0
        if (incomplete) incompleteEpochListings--
        return TaskListPage(
            page = 1, totalPages = 1, totalItems = items.size + if (incomplete) 1 else 0,
            items = items.map { it.value.copy(id = it.key, clientUpdatedAt = versionOf(it.key) ?: it.value.clientUpdatedAt) },
            updatedStamps = items.map { PbDates.formatSpace(PbDates.parse(versionOf(it.key)) ?: Instant.EPOCH) },
        )
    }

    private fun gate(taskId: String) {
        writeFailure?.let { throw it }
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

    /** Server-side `client_updated_at` by record id. A record with no entry here holds its wire stamp. */
    val versions = mutableMapOf<String, String>()
    private val changesAfterListing = mutableMapOf<String, String>()

    /** What another device does between this push's listing and its write: the version changes before the next guarded call. */
    fun changeAfterListing(recordId: String, version: String) { changesAfterListing[recordId] = version }

    /** The one version the listing shows and the guard compares, so a later run lists what an earlier one changed. */
    private fun versionOf(recordId: String): String? = versions[recordId] ?: records[recordId]?.clientUpdatedAt

    /** The hook's checks, in its order: record exists (404), version matches byte for byte (412). */
    private fun guard(recordId: String, expectedVersion: String): WireTask {
        versions += changesAfterListing
        changesAfterListing.clear()
        val record = records[recordId] ?: throw PocketBaseApi.HttpError(404, "")
        if (versionOf(recordId) != expectedVersion) throw PocketBaseApi.VersionConflict()
        return record
    }

    override suspend fun updateTaskGuarded(recordId: String, expectedVersion: String, wire: WireTask) {
        gate(wire.taskId)
        calls += "update"
        guard(recordId, expectedVersion)
        if (wire.clientUpdatedAt == expectedVersion) throw PocketBaseApi.HttpError(400, "")
        records[recordId] = wire.copy(id = recordId)
        versions[recordId] = wire.clientUpdatedAt
        afterGuardedUpdate()
    }

    override suspend fun deleteTaskGuarded(recordId: String, expectedVersion: String) {
        gate(records[recordId]?.taskId ?: "")
        calls += "deleteGuarded"
        guard(recordId, expectedVersion)
        records.remove(recordId)
        versions.remove(recordId)
    }

    override suspend fun deleteTask(recordId: String) {
        gate(records[recordId]?.taskId ?: "")
        calls += "delete"
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
        data class Case(val id: String, val payload: Int?, val row: Int?, val rowGone: Boolean, val wire: Int?, val after: Int?)
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
            else {
                // no live row but a listed record, so the update goes out with this device's default; the tombstone
                // keeps the pull from importing the record (F4 owns what happens to an unlisted task)
                remote.records["rec-${case.id}"] = WireMapper.push(payload.copy(updatedAt = now.minusSeconds(120)), "other", "user-1", defaultReminder = 15)
                local.tombstones[case.id] = now.toEpochMilli()
            }
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

    private fun deleteRow(id: Long, taskId: String) = SyncQueueEntity(
        id = id, taskId = taskId, operation = SyncOp.DELETE, timestamp = now.toEpochMilli(), payloadJson = null,
    )

    @Test
    fun `a refused token keeps a queued delete for an unlisted task`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote().apply { rejectIdentity = true }
        local.queue += deleteRow(1, "gone")
        val result = engine(remote, local).runSync(false)
        assertTrue(result.notSignedIn)
        assertEquals(0, local.queue.single().retryCount)
        assertTrue("delete" !in remote.calls && "deleteGuarded" !in remote.calls)
    }

    @Test
    fun `a refused token keeps a queued update for an unlisted task`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote().apply { rejectIdentity = true }
        val t = task("unlisted")
        local.tasks[t.id] = t
        local.queue += queueItem(1, t)
        val result = engine(remote, local).runSync(false)
        assertTrue(result.notSignedIn)
        assertEquals(0, local.queue.single().retryCount)
        assertTrue(remote.records.isEmpty())
        assertTrue("create" !in remote.calls && "update" !in remote.calls)
    }

    // F4 drops a row for a task this device no longer holds, but only once the server has named this account: a
    // refused session lists as a guest, so an unlisted task proves nothing.
    @Test
    fun `a refused token keeps a queued update for a task with no live row`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote().apply { rejectIdentity = true }
        local.queue += queueItem(1, task("no-row"))
        val result = engine(remote, local).runSync(false)
        assertTrue(result.notSignedIn)
        assertEquals(0, local.queue.single().retryCount)
        assertTrue("create" !in remote.calls)
    }

    @Test
    fun `another account's token keeps a queued update for a task with no live row`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote().apply { otherAccount = true }
        local.queue += queueItem(1, task("no-row"))
        val result = engine(remote, local).runSync(false)
        assertEquals("error", result.status)
        assertEquals(0, local.queue.single().retryCount)
        assertTrue("create" !in remote.calls)
    }

    @Test
    fun `another account's token keeps every queued row`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote().apply { otherAccount = true }
        val t = task("unlisted")
        local.tasks[t.id] = t
        local.queue += queueItem(1, t)
        local.queue += deleteRow(2, "gone")
        val result = engine(remote, local).runSync(false)
        assertEquals("error", result.status)
        assertEquals(listOf(0, 0), local.queue.map { it.retryCount })
        assertEquals(2, local.queue.size)
        assertTrue(remote.records.isEmpty())
    }

    @Test
    fun `a confirmed session drops a delete for an unlisted task`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        local.queue += deleteRow(1, "gone")
        val result = engine(remote, local).runSync(false)
        assertEquals("success", result.status)
        assertTrue(local.queue.isEmpty())
        assertEquals(1, remote.calls.count { it == "confirmIdentity" })
    }

    @Test
    fun `one push confirms identity once across several absences`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        local.queue += deleteRow(1, "gone-1")
        local.queue += deleteRow(2, "gone-2")
        local.queue += deleteRow(3, "gone-3")
        engine(remote, local).runSync(false)
        assertTrue(local.queue.isEmpty())
        assertEquals(1, remote.calls.count { it == "confirmIdentity" })
    }

    @Test
    fun `a push with every task listed makes no auth-refresh`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val t = task("listed", updatedAt = now.minusSeconds(100))
        local.tasks[t.id] = t
        remote.records["r1"] = WireMapper.push(task("listed", updatedAt = now.minusSeconds(500)), "other", "user-1", defaultReminder = 15)
        local.queue += queueItem(1, t)
        engine(remote, local).runSync(false)
        assertTrue("update" in remote.calls)
        assertTrue("confirmIdentity" !in remote.calls)
    }

    private fun refusedWriteEndsThePush(code: Int) = runTest {
        val local = FakeLocal(); val remote = FakeRemote().apply { writeFailure = PocketBaseApi.Unauthorized(code) }
        val first = task("first"); val second = task("second")
        remote.records["r1"] = WireMapper.push(task("first", updatedAt = now.minusSeconds(500)), "other", "user-1", defaultReminder = 15)
        remote.records["r2"] = WireMapper.push(task("second", updatedAt = now.minusSeconds(500)), "other", "user-1", defaultReminder = 15)
        local.tasks[first.id] = first; local.tasks[second.id] = second
        local.queue += queueItem(1, first)
        local.queue += queueItem(2, second)
        val result = engine(remote, local).runSync(false)
        assertTrue(result.notSignedIn)
        // The fake's gate throws before it logs a call, so the retry counts carry this test: neither row was charged.
        assertEquals(listOf(0, 0), local.queue.map { it.retryCount })
    }

    @Test
    fun `a 401 from a write ends the push without charging the row`() = refusedWriteEndsThePush(401)

    @Test
    fun `a 403 from a write ends the push without charging the row`() = refusedWriteEndsThePush(403)

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
        val partialLocal = FakeLocal().apply {
            tasks["doomed"] = task("doomed")
            queue += queueItem(1, task("doomed"), retryCount = 4, lastAttemptAt = 0L)
        }
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
        // listed, so the failure is the update's and reconciliation doesn't abandon the live row to Trash
        remote.records["rec1"] = WireMapper.push(task("doomed", updatedAt = now.minusSeconds(120)), "other", "user-1", defaultReminder = 15)
        local.tasks["doomed"] = task("doomed")
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
        listOf("first-ok", "second-blocked", "third-blocked").forEachIndexed { i, id ->
            local.tasks[id] = task(id)
            local.queue += queueItem(i + 1L, task(id), SyncOp.CREATE)
        }
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
    fun `a held-off remembered id stays for the next run`() = runTest {
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

    /**
     * SPEC-3 §8 item 2 (owner item 26, reading 2): trashed while signed out, then restored and archived. The id is
     * remembered for the Trash delete, but the archive is the user's last act, so it stands, as an archive made
     * while signed out does (rule 6). The id is still forgotten.
     */
    @Test
    fun `a remembered id with an archive row stays archived`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["rec1"] = WireMapper.push(task("archived-task", title = "remote", updatedAt = now.minusSeconds(3600)), "other-device", "user-1", defaultReminder = 15)
        local.tombstones["archived-task"] = now.toEpochMilli()
        local.archived += "archived-task"
        local.resurrect += "archived-task"
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertTrue(local.removeTombstoneCalls.isEmpty(), "the archive was undone")
        assertNull(local.tasks["archived-task"])
        assertTrue(local.resurrect.isEmpty())
    }

    /**
     * SPEC-3 rule 3, shared with web and iOS. The step rebuilt a trashed task from the wire alone, which drops the
     * time-entry notes (the wire form is lossy) and this device's reminder state, so a reminder already shown here
     * could fire again. It now merges the server copy into the Trash row, as the web does.
     */
    @Test
    fun `a resurrected task keeps this device's reminder state and time-entry notes`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val entry = TimeEntry("entry001", now.minusSeconds(7_200), now.minusSeconds(3_600), notes = "kept here")
        val trashed = task("trashed-task", title = "remote", updatedAt = now.minusSeconds(3_600)).copy(
            dueDate = now.plusSeconds(86_400), notificationSent = true, lastNotificationAt = now.minusSeconds(600),
            timeEntries = listOf(entry),
        )
        remote.records["rec1"] = WireMapper.push(trashed, "other-device", "user-1", defaultReminder = 15)
        local.tombstones[trashed.id] = now.toEpochMilli()
        local.buried[trashed.id] = trashed
        local.resurrect += trashed.id
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        val back = local.tasks.getValue(trashed.id)
        assertEquals("kept here", back.timeEntries.single().notes)
        assertEquals(true, back.notificationSent)
        assertEquals(now.minusSeconds(600), back.lastNotificationAt)
    }

    /** A task holding every field this device keeps for itself (§9.2), buried here before the edit elsewhere. */
    private val richTask = task("buried-task", title = "before", updatedAt = now.minusSeconds(7_200)).copy(
        dueDate = now.plusSeconds(86_400), notificationSent = true, lastNotificationAt = now.minusSeconds(600),
        snoozedUntil = now.plusSeconds(3_600), parentTaskId = "parent-task",
        timeEntries = listOf(TimeEntry("entry001", now.minusSeconds(7_200), now.minusSeconds(3_600), notes = "kept here")),
    )

    /** One run whose listing holds [edited], a newer server copy of the buried [richTask]. */
    private suspend fun pullBack(archived: Boolean, edited: Task = richTask.copy(title = "edited elsewhere")): Task {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["rec1"] = WireMapper.push(edited.copy(updatedAt = now.minusSeconds(60)), "other-device", "user-1", defaultReminder = 15)
        local.tombstones[richTask.id] = now.minusSeconds(3_600).toEpochMilli()
        if (archived) local.archived += richTask.id
        local.buried[richTask.id] = richTask
        SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" }).runSync(triggeredByUser = false)
        return local.tasks.getValue(richTask.id)
    }

    private fun assertDeviceFieldsKept(back: Task) {
        assertEquals("edited elsewhere", back.title)
        assertEquals(richTask.timeEntries, back.timeEntries)
        assertEquals(richTask.parentTaskId, back.parentTaskId)
        assertEquals(richTask.notificationSent, back.notificationSent)
        assertEquals(richTask.lastNotificationAt, back.lastNotificationAt)
        assertEquals(richTask.snoozedUntil, back.snoozedUntil)
    }

    /**
     * S-IOS-02 (Lead, 2026-11-07), with iOS #94's test names. A server copy edited after this device archived or
     * trashed the task brings it back (§9.4). The pull rebuilt it from the wire alone, so it lost the time-entry
     * notes, the parent link, and this device's reminder state, and a reminder already shown here could fire again.
     */
    @Test
    fun `an archived task pulled back keeps this device's fields`() = runTest {
        assertDeviceFieldsKept(pullBack(archived = true))
    }

    @Test
    fun `a trashed task pulled back keeps this device's fields`() = runTest {
        assertDeviceFieldsKept(pullBack(archived = false))
    }

    /** B-55 still applies through the buried row: a moved due date is a new reminder. */
    @Test
    fun `a trashed task pulled back with a moved due date resets its reminder state`() = runTest {
        val back = pullBack(archived = false, edited = richTask.copy(title = "edited elsewhere", dueDate = now.plusSeconds(172_800)))

        assertEquals(richTask.timeEntries, back.timeEntries)
        assertEquals(richTask.parentTaskId, back.parentTaskId)
        assertEquals(false, back.notificationSent)
        assertNull(back.lastNotificationAt)
        assertNull(back.snoozedUntil)
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
     * AND-RESURRECT-2 (A1), as web's `waitsForThisDevice`. A delete queued after the id was remembered is the user's
     * latest act. The step brought the task back anyway, the push then sent the delete, and reconciliation dropped
     * the active copy with no Trash entry. Now the task stays in Trash and the id waits for the next listing.
     */
    @Test
    fun `a remembered id with a pending delete stays in Trash and is kept`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val trashed = task("trashed-task", title = "trashed again", updatedAt = now.minusSeconds(3_600))
        remote.records["rec1"] = WireMapper.push(trashed, "other-device", "user-1", defaultReminder = 15)
        local.tombstones[trashed.id] = now.minusSeconds(60).toEpochMilli()
        local.buried[trashed.id] = trashed
        local.resurrect += trashed.id
        local.queue += queueItem(1, trashed, op = SyncOp.DELETE).copy(payloadJson = null) // a Trash delete
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertNull(local.tasks[trashed.id], "the task left Trash")
        assertEquals(now.minusSeconds(60).toEpochMilli(), local.tombstones[trashed.id])
        assertEquals(setOf(trashed.id), local.resurrect)
        assertTrue(remote.records.isEmpty(), "the delete didn't reach the server")
    }

    /** A1 for a failed delete: Android re-arms failed rows on launch and manual sync (§9.5), so it still pushes later. */
    @Test
    fun `a remembered id with a failed delete stays in Trash and is kept`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        val trashed = task("trashed-task", title = "trashed again", updatedAt = now.minusSeconds(3_600))
        remote.records["rec1"] = WireMapper.push(trashed, "other-device", "user-1", defaultReminder = 15)
        local.tombstones[trashed.id] = now.minusSeconds(60).toEpochMilli()
        local.buried[trashed.id] = trashed
        local.resurrect += trashed.id
        local.queue += queueItem(1, trashed, op = SyncOp.DELETE, retryCount = 5, status = "failed").copy(payloadJson = null)
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        engine.runSync(triggeredByUser = false)

        assertNull(local.tasks[trashed.id], "the task left Trash")
        assertEquals(now.minusSeconds(60).toEpochMilli(), local.tombstones[trashed.id])
        assertEquals(setOf(trashed.id), local.resurrect)
    }

    /**
     * AND-RESURRECT-2 (A2), as on web. The step runs between the pull and the push, so a step that threw ended the run
     * and skipped the push, and a listing that kept failing blocked every upload while any id was remembered. Now a
     * failed step forgets nothing, is reported, and the run goes on to push.
     */
    @Test
    fun `a step that fails forgets nothing and leaves the sync successful`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        local.savedCursor = "2026-07-19 11:00:00.000Z" // so the step's listing is the first from the epoch
        local.tombstones["trashed-task"] = now.toEpochMilli()
        local.resurrect += "trashed-task"
        val edited = task("edited-task", title = "edited here")
        local.tasks[edited.id] = edited
        local.queue += queueItem(1, edited)
        remote.incompleteEpochListings = 1
        val reported = mutableListOf<Exception>()
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" }, onResurrectFailed = { reported += it })

        val result = engine.runSync(triggeredByUser = false)

        assertEquals("success", result.status)
        assertEquals(1, result.pushed)
        assertEquals(listOf("edited here"), remote.records.values.map { it.title })
        assertEquals(setOf("trashed-task"), local.resurrect)
        assertEquals(now.toEpochMilli(), local.tombstones["trashed-task"])
        assertTrue(reported.single() is RemoteListingIncomplete)
    }

    /**
     * A2's first limit (the Lead's review of iOS #97, matching its test). Android pulls, runs the step, then pushes,
     * and the push and reconciliation check identity again. So a step that confirmed another account must stop the
     * run, or the push writes this device's edits to an account it just failed to confirm. The edited task is
     * unlisted, so the push would make its own identity call: exactly one call proves the step ended the run.
     */
    @Test
    fun `a step whose identity check fails stops the run before the push`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote().apply { otherAccount = true }
        local.tombstones["trashed-task"] = now.toEpochMilli()
        local.resurrect += "trashed-task"
        val edited = task("edited-task", title = "edited here")
        local.tasks[edited.id] = edited
        local.queue += queueItem(1, edited)
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

        val result = engine.runSync(triggeredByUser = false)

        assertEquals("error", result.status)
        assertEquals(0, result.pushed)
        assertTrue(remote.records.isEmpty(), "the push wrote to an unconfirmed account")
        assertEquals(setOf("trashed-task"), local.resurrect)
        assertEquals(1, remote.calls.count { it == "confirmIdentity" }, "the push ran its own identity check")
    }

    /** A2's second limit: a dead session the step's identity check finds still ends the run, so the push spends no retry. */
    @Test
    fun `a dead session found by the step pushes nothing`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote().apply { rejectIdentity = true }
        local.tombstones["trashed-task"] = now.toEpochMilli()
        local.resurrect += "trashed-task"
        val edited = task("edited-task", title = "edited here")
        local.tasks[edited.id] = edited
        local.queue += queueItem(1, edited)
        val reported = mutableListOf<Exception>()
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" }, onResurrectFailed = { reported += it })

        val result = engine.runSync(triggeredByUser = false)

        assertTrue(result.notSignedIn)
        assertEquals(0, local.queue.single().retryCount)
        assertTrue(remote.records.isEmpty())
        assertTrue(reported.isEmpty())
        assertEquals(1, remote.calls.count { it == "confirmIdentity" }, "the push ran its own identity check")
    }

    /** A2's other limit: a session that ends during the step stops the run, and isn't reported as a step failure. */
    @Test
    fun `a session that ends during the step stops the run`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        var owner = "user-1"
        remote.duringIdentityCheck = { owner = "user-2" }
        local.tombstones["trashed-task"] = now.toEpochMilli()
        local.resurrect += "trashed-task"
        val reported = mutableListOf<Exception>()
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { owner }, onResurrectFailed = { reported += it })

        val result = engine.runSync(triggeredByUser = false)

        assertEquals("error", result.status)
        assertEquals(setOf("trashed-task"), local.resurrect)
        assertTrue(reported.isEmpty())
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

    // The guarded route (ADR 0021): every update and delete of a listed record carries the version the listing showed.
    private val guardedNow = Instant.parse("2026-11-19T18:00:00.000Z")
    private val v0 = "2026-11-19T17:00:00.000Z"

    private fun guardedEngine(remote: FakeRemote, local: FakeLocal) =
        SyncEngine(remote, local, Clock.fixed(guardedNow, ZoneOffset.UTC), throttleMs = 0, ownerId = { "user-1" })

    private fun guardedTask(title: String, updatedAt: String) =
        Task(id = "task-1", title = title, urgent = false, important = false,
            createdAt = Instant.parse("2026-11-19T10:00:00.000Z"), updatedAt = Instant.parse(updatedAt), notifyBefore = 15)

    private fun listed(remote: FakeRemote, version: String, title: String = "server") {
        remote.records["rec1"] = WireMapper.push(guardedTask(title, v0), "other", "user-1", defaultReminder = 15)
            .copy(clientUpdatedAt = version)
    }

    private fun guardedQueue(local: FakeLocal, id: Long, task: Task, op: SyncOp = SyncOp.UPDATE) {
        local.tasks[task.id] = task
        local.queue += SyncQueueEntity(
            id = id, taskId = task.id, operation = op, timestamp = task.updatedAt.toEpochMilli(),
            payloadJson = TaskJson.encode(task),
        )
    }

    @Test
    fun `an update writes through the guarded route with the listed version`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        guardedQueue(local, 1, guardedTask("edit", "2026-11-19T17:30:00.000Z"))

        val result = guardedEngine(remote, local).runSync(false)

        assertEquals("edit", remote.records["rec1"]?.title)
        assertEquals("2026-11-19T17:30:00.000Z", remote.versions["rec1"])
        assertEquals(1, result.pushed)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a version changed after the listing gets 412 and the row stays uncharged`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        remote.changeAfterListing("rec1", "2026-11-19T17:00:00.500Z")
        guardedQueue(local, 1, guardedTask("edit", "2026-11-19T17:30:00.000Z"))

        val result = guardedEngine(remote, local).runSync(false)

        assertEquals("server", remote.records["rec1"]?.title)
        val row = local.queue.single()
        assertEquals(0, row.retryCount)
        assertNull(row.lastAttemptAt)
        assertEquals("pending", row.status)
        assertEquals(0, result.pushed)
        assertEquals(0, result.failedItems)
    }

    @Test
    fun `a 412 on one row lets the next row push`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        remote.records["rec2"] = WireMapper.push(guardedTask("other", v0).copy(id = "task-2"), "other", "user-1", defaultReminder = 15)
        remote.changeAfterListing("rec1", "2026-11-19T17:00:00.500Z")
        guardedQueue(local, 1, guardedTask("edit", "2026-11-19T17:30:00.000Z"))
        guardedQueue(local, 2, guardedTask("edit2", "2026-11-19T17:30:00.000Z").copy(id = "task-2"))

        val result = guardedEngine(remote, local).runSync(false)

        assertEquals("edit2", remote.records["rec2"]?.title)
        assertEquals(1, result.pushed)
        assertEquals(listOf(1L), local.queue.map { it.id })
    }

    @Test
    fun `an equal stamp sends one millisecond more`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        guardedQueue(local, 1, guardedTask("edit", v0))

        guardedEngine(remote, local).runSync(false)

        assertEquals("2026-11-19T17:00:00.001Z", remote.records["rec1"]?.clientUpdatedAt)
        assertEquals("edit", remote.records["rec1"]?.title)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `two queued updates to one task both land in one push`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        val first = guardedTask("first", "2026-11-19T17:10:00.000Z")
        val second = guardedTask("second", "2026-11-19T17:20:00.000Z")
        guardedQueue(local, 1, first)
        guardedQueue(local, 2, second)

        val result = guardedEngine(remote, local).runSync(false)

        assertEquals("second", remote.records["rec1"]?.title)
        assertEquals(2, result.pushed)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `an update then a delete of one task both land in one push`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        val edit = guardedTask("edit", "2026-11-19T17:10:00.000Z")
        guardedQueue(local, 1, edit)
        local.tasks.remove(edit.id)
        local.queue += SyncQueueEntity(
            id = 2, taskId = edit.id, operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(), payloadJson = null,
        )

        val result = guardedEngine(remote, local).runSync(false)

        assertTrue(remote.records.isEmpty())
        assertEquals(2, result.pushed)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a far-future listed version is sent unclamped`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val farFuture = "2099-01-01T00:00:00.000Z"
        listed(remote, farFuture)
        guardedQueue(local, 1, guardedTask("edit", "2026-11-19T17:30:00.000Z"))

        guardedEngine(remote, local).runSync(false)

        // the fake 412s on any other string, so a landed edit proves the raw version was the one sent
        assertEquals("edit", remote.records["rec1"]?.title)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a non-canonical listed version is sent as listed`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, "2026-11-19 17:00:00Z")
        guardedQueue(local, 1, guardedTask("edit", "2026-11-19T17:30:00.000Z"))

        guardedEngine(remote, local).runSync(false)

        assertEquals("edit", remote.records["rec1"]?.title)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a listed delete with no version is charged, not sent`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, "")
        local.queue += SyncQueueEntity(
            id = 1, taskId = "task-1", operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(), payloadJson = null,
        )

        guardedEngine(remote, local).runSync(false)

        assertEquals(1, local.queue.single().retryCount)
        assertTrue("delete" !in remote.calls && "deleteGuarded" !in remote.calls)
        assertTrue("rec1" in remote.records)
    }

    @Test
    fun `a listed delete goes through the guarded route`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        local.queue += SyncQueueEntity(
            id = 1, taskId = "task-1", operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(), payloadJson = null,
        )

        guardedEngine(remote, local).runSync(false)

        assertTrue("deleteGuarded" in remote.calls)
        assertTrue("delete" !in remote.calls)
        assertTrue(remote.records.isEmpty())
    }

    @Test
    fun `a listed delete that races an edit gets 412 and the row stays uncharged`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        remote.changeAfterListing("rec1", "2026-11-19T17:00:00.500Z")
        local.queue += SyncQueueEntity(
            id = 1, taskId = "task-1", operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(), payloadJson = null,
        )

        val result = guardedEngine(remote, local).runSync(false)

        assertTrue("rec1" in remote.records)
        val row = local.queue.single()
        assertEquals(0, row.retryCount)
        assertNull(row.lastAttemptAt)
        assertEquals(0, result.pushed)
        assertEquals(0, result.failedItems)
    }

    @Test
    fun `a 404 from the guarded route is charged`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        // listed but gone by the time of the write: the listing names a record the store no longer holds
        remote.listResponder = { _, _ ->
            val wire = WireMapper.push(guardedTask("server", v0), "other", "user-1", defaultReminder = 15).copy(id = "rec1")
            TaskListPage(page = 1, totalPages = 1, items = listOf(wire), updatedStamps = listOf(PbDates.formatSpace(Instant.parse(v0))))
        }
        guardedQueue(local, 1, guardedTask("edit", "2026-11-19T17:30:00.000Z"))

        guardedEngine(remote, local).runSync(false)

        assertEquals(1, local.queue.single().retryCount)
        assertTrue("create" !in remote.calls, "a 404 must not fall back to a create (D1)")
    }

    @Test
    fun `a 412, then the next run lands with the fresh version`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        remote.changeAfterListing("rec1", "2026-11-19T17:00:00.500Z")
        guardedQueue(local, 1, guardedTask("edit", "2026-11-19T17:30:00.000Z"))
        val engine = guardedEngine(remote, local)

        engine.runSync(false)
        assertEquals("server", remote.records["rec1"]?.title)
        val second = engine.runSync(false)

        assertEquals("edit", remote.records["rec1"]?.title)
        assertEquals("2026-11-19T17:30:00.000Z", remote.versions["rec1"])
        assertEquals(1, second.pushed)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a 412, then the next run drops the row as stale`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        remote.changeAfterListing("rec1", "2026-11-19T17:45:00.000Z")
        guardedQueue(local, 1, guardedTask("edit", "2026-11-19T17:30:00.000Z"))
        val engine = guardedEngine(remote, local)

        engine.runSync(false)
        assertEquals(1, local.queue.size)
        engine.runSync(false)

        assertEquals("server", remote.records["rec1"]?.title)
        assertTrue(local.queue.isEmpty())
        assertEquals(1, remote.calls.count { it == "update" }, "the second run sent a write the other client's stamp outranks")
    }

    @Test
    fun `a session that ends during the push's identity check sends nothing`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        var owner: String? = "user-1"
        remote.duringIdentityCheck = { owner = null }
        guardedQueue(local, 1, guardedTask("new", "2026-11-19T17:30:00.000Z"))
        val engine = SyncEngine(remote, local, Clock.fixed(guardedNow, ZoneOffset.UTC), throttleMs = 0, ownerId = { owner })

        engine.runSync(false)

        assertTrue("create" !in remote.calls)
        assertEquals(0, local.queue.single().retryCount)
    }

    @Test
    fun `an account switch during the push's identity check keeps an unlisted delete queued`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        var owner = "user-1"
        remote.duringIdentityCheck = { owner = "user-2" }
        local.queue += SyncQueueEntity(
            id = 1, taskId = "task-1", operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(), payloadJson = null,
        )
        val engine = SyncEngine(remote, local, Clock.fixed(guardedNow, ZoneOffset.UTC), throttleMs = 0, ownerId = { owner })

        engine.runSync(false)

        assertEquals(listOf(1L), local.queue.map { it.id })
    }

    // A bumped write (equal stamp plus 1 ms) leaves the server ahead of the row, so the row is restamped (F1)
    // and a delete in the same push recognizes the version this push wrote (F2).
    private val v0Bumped = Instant.parse("2026-11-19T17:00:00.001Z")

    private fun archiveDelete(id: Long, archived: Task) = SyncQueueEntity(
        id = id, taskId = archived.id, operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(),
        payloadJson = TaskJson.encode(archived),
    )

    /** `ArchiveStore.archive`: the queue rows for the task go, the delete carries the task as archived, and the archive row exists. */
    private fun archive(local: FakeLocal, queueId: Long, taskId: String = "task-1"): Task {
        val archived = local.tasks.remove(taskId)!!
        local.queue.removeAll { it.taskId == taskId }
        local.queue += archiveDelete(queueId, archived)
        local.tombstones[taskId] = guardedNow.toEpochMilli()
        local.archived += taskId
        return archived
    }

    @Test
    fun `a bumped update restamps the local row and queues nothing`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        guardedQueue(local, 1, guardedTask("edit", v0))

        guardedEngine(remote, local).runSync(false)

        assertEquals(v0Bumped, local.tasks["task-1"]?.updatedAt)
        assertEquals("edit", local.tasks["task-1"]?.title)
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `an archive queued after a bumped update deletes the server copy`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        guardedQueue(local, 1, guardedTask("edit", v0))
        guardedEngine(remote, local).runSync(false)
        archive(local, 2)

        guardedEngine(remote, local).runSync(false)

        assertTrue(remote.records.isEmpty(), "the delete was dropped as a newer remote edit")
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a bumped update and an archive delete of one task both land in one push`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        val edit = guardedTask("edit", v0)
        guardedQueue(local, 1, edit)
        local.tasks.remove(edit.id)
        local.queue += archiveDelete(2, edit)
        local.tombstones[edit.id] = guardedNow.toEpochMilli()
        local.archived += edit.id

        val result = guardedEngine(remote, local).runSync(false)

        assertEquals(listOf("update", "deleteGuarded"), remote.calls.filter { it == "update" || it == "deleteGuarded" })
        assertTrue(remote.records.isEmpty(), "the delete was dropped as a newer remote edit")
        assertEquals(2, result.pushed)
    }

    @Test
    fun `a local edit made after the update was queued is not overwritten by the restamp`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        guardedQueue(local, 1, guardedTask("edit", v0))
        val newer = guardedTask("edited again", "2026-11-19T17:45:00.000Z")
        local.tasks[newer.id] = newer

        guardedEngine(remote, local).runSync(false)

        assertEquals(newer, local.tasks["task-1"])
    }

    @Test
    fun `a session that ends during the update leaves the local row untouched`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        var owner: String? = "user-1"
        remote.afterGuardedUpdate = { owner = null }
        listed(remote, v0)
        val edit = guardedTask("edit", v0)
        guardedQueue(local, 1, edit)
        val engine = SyncEngine(remote, local, Clock.fixed(guardedNow, ZoneOffset.UTC), throttleMs = 0, ownerId = { owner })

        val result = engine.runSync(false)

        assertEquals(listOf("update"), remote.calls.filter { it == "update" })
        assertEquals(edit, local.tasks["task-1"])
        assertEquals(0, local.queue.single().retryCount, "the row was charged for a session that ended")
        assertEquals("error", result.status)
        assertEquals("Sync session ended before this write", result.error)
    }

    @Test
    fun `a task archived while its bumped update is in flight is deleted by the next push`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        guardedQueue(local, 1, guardedTask("edit", v0))
        remote.afterGuardedUpdate = { archive(local, 2) }
        guardedEngine(remote, local).runSync(false)
        remote.afterGuardedUpdate = {}

        guardedEngine(remote, local).runSync(false)

        assertTrue(remote.records.isEmpty(), "the delete was dropped as a newer remote edit")
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a task archived earlier in the same push is deleted by the next push`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        remote.records["rec0"] = WireMapper.push(guardedTask("first", v0).copy(id = "task-0"), "other", "user-1", defaultReminder = 15)
        guardedQueue(local, 1, guardedTask("first", "2026-11-19T17:10:00.000Z").copy(id = "task-0"))
        guardedQueue(local, 2, guardedTask("edit", v0))
        // the user archives task-1 while the push is still on task-0: the snapshot still holds task-1's update
        remote.afterGuardedUpdate = { if (local.tasks.containsKey("task-1")) archive(local, 3) }
        guardedEngine(remote, local).runSync(false)
        remote.afterGuardedUpdate = {}

        guardedEngine(remote, local).runSync(false)

        assertNull(remote.records["rec1"], "the delete was dropped as a newer remote edit")
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `another device's edit at exactly one millisecond past the archived version still makes the delete stale`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, "2026-11-19T17:00:00.001Z") // device_id "other"
        val archived = guardedTask("done", v0)
        local.queue += archiveDelete(1, archived)
        local.tombstones[archived.id] = guardedNow.toEpochMilli()
        local.archived += archived.id

        guardedEngine(remote, local).runSync(false)

        assertEquals("server", remote.records["rec1"]?.title)
        assertTrue("deleteGuarded" !in remote.calls)
        assertTrue(local.queue.isEmpty())
    }

    // The creates half of F2: a created record's stamp is the one this push wrote, so a delete queued with an older
    // archived version (an out-of-order queue from an older build) is not read as another device's edit.
    @Test
    fun `a delete of a task this push created is not stale`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val created = guardedTask("new", "2026-11-19T17:30:00.000Z")
        guardedQueue(local, 1, created, SyncOp.CREATE) // the row stays until the push has sent the create
        local.queue += archiveDelete(2, created.copy(updatedAt = Instant.parse("2026-11-19T17:10:00.000Z")))

        val result = guardedEngine(remote, local).runSync(false)

        assertEquals(listOf("create", "deleteGuarded"), remote.calls.filter { it == "create" || it == "deleteGuarded" })
        assertEquals(2, result.pushed)
    }

    @Test
    fun `an update that was not bumped leaves the local row's stamp alone`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        val edit = guardedTask("edit", "2026-11-19T17:30:00.000Z")
        guardedQueue(local, 1, edit)

        guardedEngine(remote, local).runSync(false)

        assertEquals(edit.updatedAt, local.tasks["task-1"]?.updatedAt)
    }

    @Test
    fun `the next sync after a bumped write pulls nothing and reports no conflict`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        guardedQueue(local, 1, guardedTask("edit", v0))
        val engine = guardedEngine(remote, local)
        engine.runSync(false)

        val next = engine.runSync(false)

        assertEquals(0, next.pulled)
        assertEquals(0, next.conflicts)
        assertEquals("edit", local.tasks["task-1"]?.title)
    }

    @Test
    fun `this device's id at two milliseconds past the archived version still makes the delete stale`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, "2026-11-19T17:00:00.002Z")
        remote.records["rec1"] = remote.records.getValue("rec1").copy(deviceId = "this-device")
        val archived = guardedTask("done", v0)
        local.queue += archiveDelete(1, archived)
        local.tombstones[archived.id] = guardedNow.toEpochMilli()
        local.archived += archived.id

        guardedEngine(remote, local).runSync(false)

        assertEquals("server", remote.records["rec1"]?.title)
        assertTrue("deleteGuarded" !in remote.calls)
    }

    // F4: a charged update row left queued for a task this device no longer holds comes due after the delete landed.
    private fun chargedUpdate(local: FakeLocal, task: Task) {
        local.queue += SyncQueueEntity(
            id = 1, taskId = task.id, operation = SyncOp.UPDATE, timestamp = task.updatedAt.toEpochMilli(),
            payloadJson = TaskJson.encode(task), retryCount = 1, lastAttemptAt = guardedNow.toEpochMilli(),
        )
    }

    private fun laterEngine(remote: FakeRemote, local: FakeLocal) =
        SyncEngine(remote, local, Clock.fixed(guardedNow.plusSeconds(60), ZoneOffset.UTC), throttleMs = 0, ownerId = { "user-1" })

    @Test
    fun `a charged update for a task moved to Trash never re-creates it`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        val edit = guardedTask("edit", "2026-11-19T17:30:00.000Z")
        chargedUpdate(local, edit)
        // TaskStore.delete queues a tombstone and leaves the task's other rows alone
        local.queue += SyncQueueEntity(id = 2, taskId = edit.id, operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(), payloadJson = null)
        local.trashed[edit.id] = guardedNow.toEpochMilli()
        local.tombstones[edit.id] = guardedNow.toEpochMilli()
        guardedEngine(remote, local).runSync(false) // the delete lands; the update is still backing off
        assertTrue(remote.records.isEmpty())

        laterEngine(remote, local).runSync(false)

        assertTrue("create" !in remote.calls)
        assertTrue(remote.records.isEmpty())
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `a charged update for an archived task never re-creates it`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        val edit = guardedTask("edit", v0)
        local.tasks[edit.id] = edit
        chargedUpdate(local, edit)
        archive(local, 2) // ArchiveStore clears the task's queued rows, so only the delete remains
        guardedEngine(remote, local).runSync(false)

        laterEngine(remote, local).runSync(false)

        assertTrue("create" !in remote.calls)
        assertTrue(remote.records.isEmpty())
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `an update for a live task with no listed record still creates it`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        val live = guardedTask("live", "2026-11-19T17:30:00.000Z")
        guardedQueue(local, 1, live)

        val result = guardedEngine(remote, local).runSync(false)

        assertEquals(listOf("create"), remote.calls.filter { it == "create" })
        assertEquals(1, result.pushed)
    }

    // AC14: a writer whose clock runs ahead. The pull reads the archive as older than the listed stamp and brings the task
    // back, so the push must read the delete as stale too, or the server copy goes and reconcile removes the live task.
    private val skewed = "2026-11-19T18:02:00.000Z" // two minutes past guardedNow, inside the clock-skew ceiling

    @Test
    fun `with a skewed clock an update and an archive delete in one push leave the task live`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, skewed)
        val edit = guardedTask("edit", skewed)
        guardedQueue(local, 1, edit)
        local.tasks.remove(edit.id)
        local.queue += archiveDelete(2, edit)
        local.tombstones[edit.id] = guardedNow.toEpochMilli()
        local.archived += edit.id

        guardedEngine(remote, local).runSync(false)

        assertEquals(1, remote.records.size, "the server copy was deleted")
        assertTrue(edit.id in local.tasks, "the live task was removed")
    }

    @Test
    fun `with a skewed clock an archive made while the bumped update was in flight leaves the task live`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, skewed)
        guardedQueue(local, 1, guardedTask("edit", skewed))
        remote.afterGuardedUpdate = { archive(local, 2) } // the delete carries the stamp the update was queued with
        guardedEngine(remote, local).runSync(false)
        remote.afterGuardedUpdate = {}

        guardedEngine(remote, local).runSync(false)

        assertEquals(1, remote.records.size, "the server copy was deleted")
        assertTrue("task-1" in local.tasks, "the live task was removed")
    }

    @Test
    fun `with a skewed clock an update and a Trash delete in one push leave the task live`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, skewed)
        val edit = guardedTask("edit", skewed)
        guardedQueue(local, 1, edit)
        local.tasks.remove(edit.id) // moved to Trash: a tombstone row with no payload
        local.queue += SyncQueueEntity(id = 2, taskId = edit.id, operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(), payloadJson = null)
        local.trashed[edit.id] = guardedNow.toEpochMilli()
        local.tombstones[edit.id] = guardedNow.toEpochMilli()

        guardedEngine(remote, local).runSync(false)

        assertEquals(1, remote.records.size, "the server copy was deleted")
        assertTrue(edit.id in local.tasks, "the live task was removed")
    }

    // AC15: only this device's own id marks a bump as its own.
    @Test
    fun `a listed record with no device id at one millisecond past the archived version keeps the delete stale`() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, "2026-11-19T17:00:00.001Z")
        remote.records["rec1"] = remote.records.getValue("rec1").copy(deviceId = "")
        val archived = guardedTask("done", v0)
        local.queue += archiveDelete(1, archived)
        local.tombstones[archived.id] = guardedNow.toEpochMilli()
        local.archived += archived.id

        guardedEngine(remote, local).runSync(false)

        assertEquals("server", remote.records["rec1"]?.title)
        assertTrue("deleteGuarded" !in remote.calls)
    }

    // I2 and its archive twin. A delete that failed once waits out its 5 s backoff, so the run 2 s after the undo pushes
    // the undo's create alone, and the run at 60 s finds any delete still queued due.
    private fun engineAfter(remote: FakeRemote, local: FakeLocal, seconds: Long) =
        SyncEngine(remote, local, Clock.fixed(guardedNow.plusSeconds(seconds), ZoneOffset.UTC), throttleMs = 0, ownerId = { "user-1" })

    /** `TaskStore.delete`: a tombstone row with no payload and a Trash row. The task's other queue rows stay. */
    private fun trash(local: FakeLocal, queueId: Long, taskId: String = "task-1") {
        local.tasks.remove(taskId)
        local.queue += SyncQueueEntity(
            id = queueId, taskId = taskId, operation = SyncOp.DELETE, timestamp = guardedNow.toEpochMilli(), payloadJson = null,
        )
        local.trashed[taskId] = guardedNow.toEpochMilli()
        local.tombstones[taskId] = guardedNow.toEpochMilli()
    }

    /** Back in the live table with its Trash or archive row gone, and a create queued for it. */
    private fun bringBack(local: FakeLocal, queueId: Long, task: Task, queuedAt: Instant) {
        local.queue += SyncQueueEntity(
            id = queueId, taskId = task.id, operation = SyncOp.CREATE, timestamp = queuedAt.toEpochMilli(),
            payloadJson = TaskJson.encode(task),
        )
        local.tasks[task.id] = task
        local.trashed.remove(task.id)
        local.archived.remove(task.id)
        local.tombstones.remove(task.id)
    }

    // The engine half of I2. TrashStore.restore now removes the waiting delete in the transaction that queues the
    // create (androidTest restoreRemovesTheWaitingDelete), so this models the queue it leaves. Undo keeps the task's
    // stamps, so a delete left queued would not look stale after the PATCH: it would land, and reconcile would drop
    // the task.
    @Test
    fun undoAfterAFailedTrashDeleteKeepsTheTask() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        val synced = guardedTask("server", v0)
        trash(local, 1)
        remote.failOnTaskIds += synced.id
        guardedEngine(remote, local).runSync(false)
        assertEquals(1, local.queue.single().retryCount, "the delete was not charged")
        remote.failOnTaskIds.clear()
        remote.calls.clear()

        // TrashStore.restore: the delete goes, and the create carries the task with its stamps kept
        local.queue.removeAll { it.taskId == synced.id && it.operation == SyncOp.DELETE }
        bringBack(local, 2, synced, queuedAt = guardedNow.plusSeconds(1))
        engineAfter(remote, local, 2).runSync(false)
        laterEngine(remote, local).runSync(false)

        assertTrue("update" in remote.calls)
        assertTrue("deleteGuarded" !in remote.calls)
        assertEquals(synced.id, remote.records["rec1"]?.taskId)
        assertTrue(synced.id in local.tasks, "the restored task was removed")
        assertTrue(local.queue.isEmpty())
    }

    // Pin, expected green on main. ArchiveStore.restore stamps a fresh updatedAt and leaves the waiting delete queued.
    // Once the PATCH lands that stamp, the delete reads as stale and is dropped, so archive needs no store change.
    @Test
    fun archiveRestoreAfterAFailedDeleteKeepsTheTask() = runTest {
        val local = FakeLocal(); val remote = FakeRemote()
        listed(remote, v0)
        local.tasks["task-1"] = guardedTask("done", v0)
        val archived = archive(local, 1)
        remote.failOnTaskIds += archived.id
        guardedEngine(remote, local).runSync(false)
        assertEquals(1, local.queue.single().retryCount, "the delete was not charged")
        remote.failOnTaskIds.clear()
        remote.calls.clear()

        val restoredAt = guardedNow.plusSeconds(1)
        bringBack(local, 2, archived.copy(updatedAt = restoredAt), queuedAt = restoredAt)
        engineAfter(remote, local, 2).runSync(false)
        laterEngine(remote, local).runSync(false)

        assertTrue("update" in remote.calls)
        assertTrue("deleteGuarded" !in remote.calls)
        assertEquals(archived.id, remote.records["rec1"]?.taskId)
        assertTrue(archived.id in local.tasks, "the restored task was removed")
        assertTrue(local.queue.isEmpty())
    }
}
