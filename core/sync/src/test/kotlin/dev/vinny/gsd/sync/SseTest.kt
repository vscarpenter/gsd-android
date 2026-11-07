package dev.vinny.gsd.sync

import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeEntry
import dev.vinny.gsd.store.SyncQueueEntity
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Minimal fake for the SSE apply path's tombstone guard — only tombstoneAtMs/removeTombstone matter here. */
private class FakeSseLocal : SyncLocalStore {
    val tombstones = mutableMapOf<String, Long>()
    val removeTombstoneCalls = mutableListOf<String>()

    override suspend fun activeTasks(): List<Task> = emptyList()
    override suspend fun taskById(id: String): Task? = null
    override suspend fun upsertFromRemote(task: Task) = Unit
    override suspend fun deleteFromRemote(id: String) = Unit
    override suspend fun abandonToTrash(id: String, deletedAtMs: Long) = Unit
    override suspend fun pendingQueue(): List<SyncQueueEntity> = emptyList()
    override suspend fun failedQueue(): List<SyncQueueEntity> = emptyList()
    override suspend fun markQueueItemDone(id: Long) = Unit
    override suspend fun bumpQueueRetry(id: Long, error: String, attemptAtMs: Long, nowFailed: Boolean) = Unit
    override suspend fun requeueFailed() = Unit
    override suspend fun cursor(): String? = null
    override suspend fun saveCursor(cursor: String) = Unit
    override suspend fun clearCursor() = Unit
    override suspend fun pullContractVersion(): Int = SyncEngine.PULL_CONTRACT_VERSION
    override suspend fun savePullContractVersion(version: Int) = Unit
    override suspend fun enqueueUpdate(task: Task, nowMs: Long) = Unit
    override suspend fun deviceId(): String = "this-device"
    override suspend fun insertHistory(entry: SyncHistoryEntry) = Unit
    override suspend fun tombstoneAtMs(id: String): Long? = tombstones[id]
    override suspend fun removeTombstone(id: String) {
        tombstones.remove(id)
        removeTombstoneCalls += id
    }
    override suspend fun resurrectOnSight(): Set<String> = emptySet()
    override suspend fun rememberResurrectOnSight(ids: Set<String>) = Unit
    override suspend fun forgetResurrectOnSight(ids: Set<String>) = Unit
    override suspend fun defaultReminderMinutes(): Int = 15
    override suspend fun settleNotifyBefore(id: String, minutes: Int) = Unit
    override suspend fun isArchived(id: String): Boolean = false
    override suspend fun buriedTask(id: String): Task? = null
}

class SseTest {

    private fun feed(parser: SseParser, raw: String): List<SseEvent> =
        raw.lines().flatMap { parser.feedLine(it) }

    @Test
    fun `frames parse with heartbeats, no-space colons, and multi-line data`() {
        val parser = SseParser()
        val events = feed(
            parser,
            """
            |id:abc123
            |event:PB_CONNECT
            |data:{"clientId":"client-1"}
            |
            |: heartbeat comment
            |event: tasks
            |data: {"record":{"task_id":"t1"},
            |data: "action":"create"}
            |
            """.trimMargin(),
        )
        assertEquals(2, events.size)
        assertEquals("PB_CONNECT", events[0].event)
        assertEquals("""{"clientId":"client-1"}""", events[0].data)
        assertEquals("tasks", events[1].event)
        assertTrue(events[1].data.contains("\n")) // multi-line data joined with newline
    }

    @Test
    fun `pb_connect client id extraction`() {
        assertEquals("client-1", SseParser.clientId(SseEvent(null, "PB_CONNECT", """{"clientId":"client-1"}""")))
        assertNull(SseParser.clientId(SseEvent(null, "PB_CONNECT", """{"nope":true}""")))
    }

    @Test
    fun `owner fail-closed drops foreign and keeps own or empty`() {
        val record = wire("t1", owner = "someone-else", deviceId = "other-device")
        assertTrue(
            SseApplyRules.decide("create", record, ownUserId = "me", ownDeviceId = "this-device",
                pendingDeleteTaskIds = emptySet(), pendingAnyTaskIds = emptySet())
                is SseDecision.Drop,
        )
        val own = wire("t1", owner = "me", deviceId = "other-device")
        assertTrue(
            SseApplyRules.decide("create", own, "me", "this-device", emptySet(), emptySet())
                is SseDecision.ApplyUpsert,
        )
        val emptyOwner = wire("t1", owner = "", deviceId = "other-device")
        assertTrue(
            SseApplyRules.decide("update", emptyOwner, "me", "this-device", emptySet(), emptySet())
                is SseDecision.ApplyUpsert,
        )
    }

    @Test
    fun `create-update echo filter and pending-delete protection`() {
        val echo = wire("t1", owner = "me", deviceId = "this-device")
        assertTrue(
            SseApplyRules.decide("update", echo, "me", "this-device", emptySet(), emptySet())
                is SseDecision.Drop,
        )
        val foreign = wire("t1", owner = "me", deviceId = "other")
        assertTrue(
            SseApplyRules.decide("update", foreign, "me", "this-device",
                pendingDeleteTaskIds = setOf("t1"), pendingAnyTaskIds = setOf("t1"))
                is SseDecision.Drop, // don't resurrect a locally-deleted task
        )
    }

    @Test
    fun `deletes are not echo-filtered but any pending queue item protects`() {
        val deletion = wire("t1", owner = "me", deviceId = "this-device") // own device id on deletes is fine
        assertTrue(
            SseApplyRules.decide("delete", deletion, "me", "this-device", emptySet(), emptySet())
                is SseDecision.ApplyDelete,
        )
        assertTrue(
            SseApplyRules.decide("delete", deletion, "me", "this-device",
                pendingDeleteTaskIds = emptySet(), pendingAnyTaskIds = setOf("t1"))
                is SseDecision.Drop,
        )
    }

    @Test
    fun `sse guard drops a create-update whose tombstone is not strictly older`() = runTest {
        val local = FakeSseLocal()
        local.tombstones["t1"] = 1_000L // archived/trashed at 1000ms

        assertFalse(resolveTombstone(local, "t1", Instant.ofEpochMilli(1_000L), Instant.ofEpochMilli(1_000L))) // equal → drop, fail closed

        assertEquals(1_000L, local.tombstones["t1"]) // tombstone still present
        assertTrue(local.removeTombstoneCalls.isEmpty())
    }

    @Test
    fun `sse guard resurrects when the remote edit is strictly newer than the tombstone`() = runTest {
        val local = FakeSseLocal()
        local.tombstones["t1"] = 1_000L

        assertTrue(resolveTombstone(local, "t1", Instant.ofEpochMilli(1_001L), Instant.ofEpochMilli(1_001L)))

        assertEquals(listOf("t1"), local.removeTombstoneCalls) // removed exactly once
        assertNull(local.tombstones["t1"])
    }

    @Test
    fun `sse guard never resurrects on an unparseable remote timestamp`() = runTest {
        val local = FakeSseLocal()
        local.tombstones["t1"] = 1_000L

        assertFalse(resolveTombstone(local, "t1", null, Instant.ofEpochMilli(1_000L)))

        assertEquals(1_000L, local.tombstones["t1"]) // untouched, fail closed
        assertTrue(local.removeTombstoneCalls.isEmpty())
    }

    private fun wire(taskId: String, owner: String, deviceId: String) =
        WireTask(taskId = taskId, owner = owner, deviceId = deviceId,
            clientUpdatedAt = "2026-06-10T12:00:00.000Z")

    private fun wire(taskId: String, updatedAtMs: Long) = WireMapper.push(
        Task(id = taskId, title = "remote", urgent = true, important = true,
            createdAt = Instant.EPOCH, updatedAt = Instant.ofEpochMilli(updatedAtMs)),
        "other-device", "user-1", defaultReminder = 15,
    )

    private fun localTask(id: String, updatedAtMs: Long) = Task(id = id, title = "local", urgent = true,
        important = true, createdAt = Instant.EPOCH, updatedAt = Instant.ofEpochMilli(updatedAtMs))

    @Test
    fun `a realtime upsert never brings back a trashed task it is not newer than`() = runTest {
        val local = FakeLocal().apply { tombstones["task-0001"] = 1_000L }
        applyRealtimeUpsert(local, wire("task-0001", 1_000L), Instant.ofEpochMilli(1_000L)) { true }
        assertNull(local.tasks["task-0001"])
    }

    @Test
    fun `a strictly newer realtime upsert brings a trashed task back once`() = runTest {
        val local = FakeLocal().apply { tombstones["task-0001"] = 1_000L }
        applyRealtimeUpsert(local, wire("task-0001", 1_001L), Instant.ofEpochMilli(1_001L)) { true }
        assertEquals("remote", local.tasks["task-0001"]?.title)
        assertEquals(listOf("task-0001"), local.removeTombstoneCalls)
    }

    /** S-IOS-02, the realtime half (iOS #94's name): the task comes back merged into its Trash row, as the pull's does. */
    @Test
    fun `a trashed task brought back by realtime keeps this device's fields`() = runTest {
        val entry = TimeEntry("entry001", Instant.ofEpochMilli(100L), Instant.ofEpochMilli(600_100L), notes = "kept here")
        val trashed = localTask("task-0001", 500L).copy(
            dueDate = Instant.ofEpochMilli(90_000_000L), notificationSent = true, lastNotificationAt = Instant.ofEpochMilli(800L),
            snoozedUntil = Instant.ofEpochMilli(5_000L), parentTaskId = "parent-task", timeEntries = listOf(entry),
        )
        val local = FakeLocal().apply {
            tombstones[trashed.id] = 1_000L
            buried[trashed.id] = trashed
        }
        val editedElsewhere = trashed.copy(title = "edited elsewhere", updatedAt = Instant.ofEpochMilli(1_001L))
        val record = WireMapper.push(editedElsewhere, "other-device", "user-1", defaultReminder = 15)

        applyRealtimeUpsert(local, record, Instant.ofEpochMilli(1_001L)) { true }

        val back = local.tasks.getValue(trashed.id)
        assertEquals("edited elsewhere", back.title)
        assertEquals(trashed.timeEntries, back.timeEntries)
        assertEquals(trashed.parentTaskId, back.parentTaskId)
        assertEquals(trashed.notificationSent, back.notificationSent)
        assertEquals(trashed.lastNotificationAt, back.lastNotificationAt)
        assertEquals(trashed.snoozedUntil, back.snoozedUntil)
    }

    @Test
    fun `a realtime upsert older than the local copy changes nothing`() = runTest {
        val local = FakeLocal().apply { tasks["task-0001"] = localTask("task-0001", 2_000L) }
        applyRealtimeUpsert(local, wire("task-0001", 1_000L), Instant.ofEpochMilli(1_000L)) { true }
        assertEquals("local", local.tasks["task-0001"]?.title)
    }

    @Test
    fun `a newer realtime upsert replaces the local copy`() = runTest {
        val local = FakeLocal().apply { tasks["task-0001"] = localTask("task-0001", 1_000L) }
        applyRealtimeUpsert(local, wire("task-0001", 2_000L), Instant.ofEpochMilli(2_000L)) { true }
        assertEquals("remote", local.tasks["task-0001"]?.title)
    }

    /** Sprint backlog AND-RT-FENCE: the session is checked on arrival, and the reads before a write suspend. */
    @Test
    fun `a realtime upsert whose session ended during its reads writes nothing`() = runTest {
        var signedIn = true
        val local = object : FakeLocal() {
            override suspend fun taskById(id: String): Task? { signedIn = false; return super.taskById(id) }
        }

        applyRealtimeEvent(local, "update", wire("task-0001", 2_000L), "user-1", Instant.ofEpochMilli(2_000L)) { signedIn }

        assertNull(local.tasks["task-0001"])
    }

    @Test
    fun `a realtime delete whose session ended during its reads removes nothing`() = runTest {
        var signedIn = true
        val local = object : FakeLocal() {
            override suspend fun pendingQueue(): List<SyncQueueEntity> { signedIn = false; return super.pendingQueue() }
        }
        local.tasks["task-0001"] = localTask("task-0001", 1_000L)

        applyRealtimeEvent(local, "delete", wire("task-0001", 2_000L), "user-1", Instant.ofEpochMilli(2_000L)) { signedIn }

        assertEquals("local", local.tasks["task-0001"]?.title)
    }

    /** The wiring the coordinator used to hold: a live session still applies both kinds of event. */
    @Test
    fun `a realtime event with the session intact still applies`() = runTest {
        val local = FakeLocal()

        applyRealtimeEvent(local, "create", wire("task-0001", 2_000L), "user-1", Instant.ofEpochMilli(2_000L)) { true }
        assertEquals("remote", local.tasks["task-0001"]?.title)

        applyRealtimeEvent(local, "delete", wire("task-0001", 2_000L), "user-1", Instant.ofEpochMilli(2_000L)) { true }
        assertNull(local.tasks["task-0001"])
    }
}
