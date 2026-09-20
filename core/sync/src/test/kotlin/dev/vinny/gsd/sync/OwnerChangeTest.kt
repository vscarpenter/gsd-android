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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Review finding S3: a different account must never cost the user the tasks they chose to keep. */
class OwnerChangeTest {
    private val now = Instant.parse("2026-09-20T12:00:00.000Z")
    private val staleCursor = "2026-09-19 08:00:00.000Z"

    private fun task(id: String, updatedAt: Instant = now.minusSeconds(3600)) =
        Task(id = id, title = id, urgent = false, important = false,
            createdAt = now.minusSeconds(7200), updatedAt = updatedAt)

    /** Two tasks already on the old account (no queue rows) and one that never left the device. */
    private fun localAfterAccountA() = FakeLocal().apply {
        savedCursor = staleCursor
        tasks["synced-1"] = task("synced-1")
        tasks["synced-2"] = task("synced-2")
        tasks["unsent"] = task("unsent")
        queue += SyncQueueEntity(id = 1, taskId = "unsent", operation = SyncOp.CREATE, timestamp = 0,
            payloadJson = TaskJson.encode(tasks.getValue("unsent")))
    }

    @Test
    fun `a new owner who keeps local tasks seeds exactly the ones with no queue row`() = runTest {
        val local = localAfterAccountA()

        OwnerChange.adopt(local, previousOwner = "user-a", newOwner = "user-b", keepLocalTasks = true, nowMs = 42)

        val seeded = local.queue.filter { it.id != 1L }
        assertEquals(setOf("synced-1", "synced-2"), seeded.map { it.taskId }.toSet())
        assertTrue(seeded.all { it.operation == SyncOp.UPDATE && it.status == "pending" && it.timestamp == 42L })
        assertEquals(1, local.queue.count { it.taskId == "unsent" }) // it already had a row: no duplicate
    }

    @Test
    fun `a seeded row carries the task exactly as stored so last-write-wins still decides`() = runTest {
        val local = localAfterAccountA()

        OwnerChange.adopt(local, previousOwner = "user-a", newOwner = "user-b", keepLocalTasks = true, nowMs = 42)

        val seeded = local.queue.singleOrNull { it.taskId == "synced-1" }
        assertNotNull(seeded, "synced-1 was never seeded")
        assertEquals(TaskJson.encode(local.tasks.getValue("synced-1")), seeded.payloadJson) // no restamp
    }

    @Test
    fun `a new owner clears the pull cursor whether tasks are kept or not`() = runTest {
        val kept = localAfterAccountA()
        val fresh = FakeLocal().apply { savedCursor = staleCursor } // Start fresh erased the tasks first

        OwnerChange.adopt(kept, previousOwner = "user-a", newOwner = "user-b", keepLocalTasks = true, nowMs = 42)
        OwnerChange.adopt(fresh, previousOwner = "user-a", newOwner = "user-b", keepLocalTasks = false, nowMs = 42)

        assertNull(kept.savedCursor)
        assertNull(fresh.savedCursor)
    }

    @Test
    fun `a new owner who starts fresh seeds nothing`() = runTest {
        val local = localAfterAccountA()

        OwnerChange.adopt(local, previousOwner = "user-a", newOwner = "user-b", keepLocalTasks = false, nowMs = 42)

        assertEquals(listOf(1L), local.queue.map { it.id })
    }

    @Test
    fun `the same owner signing in again changes nothing`() = runTest {
        val local = localAfterAccountA()

        OwnerChange.adopt(local, previousOwner = "user-a", newOwner = "user-a", keepLocalTasks = true, nowMs = 42)

        assertEquals(staleCursor, local.savedCursor) // a remote delete made while signed out must still land
        assertEquals(listOf(1L), local.queue.map { it.id })
    }

    @Test
    fun `a first sign-in changes nothing`() = runTest {
        val local = localAfterAccountA()

        OwnerChange.adopt(local, previousOwner = null, newOwner = "user-a", keepLocalTasks = true, nowMs = 42)

        assertEquals(staleCursor, local.savedCursor)
        assertEquals(listOf(1L), local.queue.map { it.id })
    }

    @Test
    fun `kept tasks survive the first run against the new account and land there`() = runTest {
        val local = localAfterAccountA()
        val accountB = FakeRemote() // empty: the new account has never seen these tasks
        OwnerChange.adopt(local, previousOwner = "user-a", newOwner = "user-b", keepLocalTasks = true, nowMs = 42)

        SyncEngine(accountB, local, Clock.fixed(now, ZoneOffset.UTC), throttleMs = 0, ownerId = { "user-b" })
            .runSync(triggeredByUser = true)

        assertEquals(setOf("synced-1", "synced-2", "unsent"), local.tasks.keys)
        assertTrue(local.remoteDeletes.isEmpty())
        assertEquals(setOf("synced-1", "synced-2", "unsent"), accountB.records.values.map { it.taskId }.toSet())
        assertTrue(accountB.records.values.all { it.owner == "user-b" })
    }
}
