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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeLocal : SyncLocalStore {
    val tasks = mutableMapOf<String, Task>()
    val queue = mutableListOf<SyncQueueEntity>()
    val history = mutableListOf<SyncHistoryEntry>()
    var savedCursor: String? = null
    val remoteDeletes = mutableListOf<String>()

    override suspend fun activeTasks(): List<Task> = tasks.values.toList()
    override suspend fun taskById(id: String): Task? = tasks[id]
    override suspend fun upsertFromRemote(task: Task) { tasks[task.id] = task }
    override suspend fun deleteFromRemote(id: String) { tasks.remove(id); remoteDeletes += id }
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
    override suspend fun deviceId(): String = "this-device"
    override suspend fun insertHistory(entry: SyncHistoryEntry) { history += entry }
}

private class FakeRemote : SyncRemote {
    val records = mutableMapOf<String, WireTask>() // recordId -> wire
    var listResponder: ((Int, String) -> TaskListPage)? = null
    var failOnTaskIds = mutableSetOf<String>()
    var rateLimitAfter = Int.MAX_VALUE
    private var writes = 0
    var nextRecordId = 1

    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
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
        val recordId = "rec${nextRecordId++}"
        val stored = wire.copy(id = recordId)
        records[recordId] = stored
        return stored
    }

    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask {
        gate(wire.taskId)
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

    @Test
    fun `pull applies remote-newer, no-ops equal, keeps local-newer`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        local.tasks["stale"] = task("stale", title = "old local", updatedAt = now.minusSeconds(100))
        local.tasks["equal"] = task("equal", title = "same", updatedAt = now.minusSeconds(50))
        local.tasks["newer"] = task("newer", title = "local wins", updatedAt = now)
        remote.records["r1"] = WireMapper.push(task("stale", "remote wins", now.minusSeconds(10)), "other", "user-1")
        remote.records["r2"] = WireMapper.push(task("equal", "remote same", now.minusSeconds(50)), "other", "user-1")
        remote.records["r3"] = WireMapper.push(task("newer", "remote older", now.minusSeconds(500)), "other", "user-1")
        remote.records["r4"] = WireMapper.push(task("brand-new", "hello", now.minusSeconds(5)), "other", "user-1")

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
        remote.records["r1"] = WireMapper.push(task("aaaa", updatedAt = now.minusSeconds(30)), "other", "user-1")
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
                items = listOf(WireMapper.push(task("weird"), "other", "user-1")),
                updatedStamps = listOf("not-a-date"),
            )
        }
        engine(remote, local).runSync(false)
        assertEquals("weird", local.tasks["weird"]!!.id)
        assertNull(local.savedCursor)
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
        remote.records["r9"] = WireMapper.push(task("guarded", "remote newer", now), "other", "user-1")
        val stale = task("guarded", "stale local", now.minusSeconds(500))
        local.queue += queueItem(1, stale, SyncOp.UPDATE)

        val result = engine(remote, local).runSync(false)
        assertEquals("remote newer", remote.records["r9"]!!.title) // untouched
        assertEquals(0, result.pushed)
        assertTrue(local.queue.isEmpty()) // skipped item resolved, not retried forever
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
        remote.records["r1"] = WireMapper.push(task("kept-remote"), "other", "user-1")
        local.tasks["kept-remote"] = task("kept-remote")
        local.tasks["gone-remote"] = task("gone-remote")
        local.tasks["protected-pending"] = task("protected-pending")
        local.tasks["protected-failed"] = task("protected-failed")
        local.queue += queueItem(1, local.tasks["protected-pending"]!!, SyncOp.CREATE)
        local.queue += queueItem(2, local.tasks["protected-failed"]!!, SyncOp.CREATE,
            retryCount = 5, status = "failed")

        engine(remote, local).runSync(false)
        assertEquals(listOf("gone-remote"), local.remoteDeletes)
        assertTrue("protected-pending" in local.tasks)
        assertTrue("protected-failed" in local.tasks)
    }

    @Test
    fun `history records counts, status, and trigger`() = runTest {
        val local = FakeLocal()
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("incoming"), "other", "user-1")
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
    fun `health precedence`() {
        assertEquals(SyncHealth.OFFLINE, Health.evaluate(true, 0, 1, 5, null))
        assertEquals(SyncHealth.SESSION_EXPIRED, Health.evaluate(false, 1, 2, 5, null))
        assertEquals(SyncHealth.FAILED_ITEMS, Health.evaluate(false, 99, 2, 5, null))
        assertEquals(SyncHealth.STALE_QUEUE, Health.evaluate(false, 99, 2, 0, 2L * 60 * 60 * 1000))
        assertEquals(SyncHealth.OK, Health.evaluate(false, 99, 2, 0, 10_000))
    }
}
