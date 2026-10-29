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
 * Saved on the wire and the pull contract version (spec 2026-10-29 §2–§3; web ADR 0019,
 * iOS docs/2026-10-27-synced-field-backfill.md). The trap these pin: an upgraded build must
 * never push `saved_at: ""` for a row an older build stored without the field.
 */
class SavedSyncTest {
    private val now = Instant.parse("2026-10-29T15:00:00.000Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val savedAt = Instant.parse("2026-10-26T12:00:00.000Z")
    private val pulledAt = Instant.parse("2026-10-27T09:00:00.000Z")

    private fun task(id: String, updatedAt: Instant = pulledAt, savedAt: Instant? = null) =
        Task(id = id, title = "t", urgent = false, important = true,
            createdAt = pulledAt.minusSeconds(600), updatedAt = updatedAt, savedAt = savedAt)

    private fun engine(remote: FakeRemote, local: FakeLocal) =
        SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

    /** A remote that honors the `updated >= cursor` filter, as PocketBase does, and records each cursor. */
    private fun FakeRemote.honorCursor(updated: Instant, cursors: MutableList<String>) {
        listResponder = { _, cursor ->
            cursors += cursor
            val stamp = PbDates.formatSpace(updated)
            val items = if (stamp >= cursor) records.entries.sortedBy { it.key }.map { it.value.copy(id = it.key) } else emptyList()
            TaskListPage(page = 1, totalPages = 1, items = items, updatedStamps = items.map { stamp })
        }
    }

    private fun queueItem(id: Long, payload: Task, op: SyncOp = SyncOp.UPDATE) = SyncQueueEntity(
        id = id, taskId = payload.id, operation = op, timestamp = now.toEpochMilli(),
        payloadJson = TaskJson.encode(payload),
    )

    @Test
    fun `an older build's cursor re-pulls once and merges the field it dropped`() = runTest {
        val local = FakeLocal().apply {
            tasks["s1"] = task("s1") // pulled by 0.10.0, which dropped saved_at
            savedCursor = PbDates.cursorAfter(pulledAt.plusSeconds(3_600)) // already past the record
            contractVersion = 0
        }
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("s1", savedAt = savedAt), "mac", "user-1")
        val cursors = mutableListOf<String>()
        remote.honorCursor(pulledAt, cursors)

        val result = engine(remote, local).runSync(false)

        assertEquals("1970-01-01 00:00:00.000Z", cursors.first())
        assertEquals(savedAt, local.tasks["s1"]!!.savedAt)
        assertEquals(0, result.conflicts)
        assertEquals(SyncEngine.PULL_CONTRACT_VERSION, local.contractVersion)
        assertEquals(PbDates.cursorAfter(pulledAt.plusSeconds(3_600)), local.savedCursor) // never moved back
        assertTrue(local.queue.isEmpty())
    }

    @Test
    fun `after the backfill the next pull is incremental again`() = runTest {
        val stored = PbDates.cursorAfter(pulledAt.plusSeconds(3_600))
        val local = FakeLocal().apply { tasks["s1"] = task("s1"); savedCursor = stored; contractVersion = 0 }
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("s1", savedAt = savedAt), "mac", "user-1")
        val cursors = mutableListOf<String>()
        remote.honorCursor(pulledAt, cursors)

        engine(remote, local).runSync(false)
        cursors.clear()
        engine(remote, local).runSync(false)

        assertEquals(stored, cursors.first())
    }

    @Test
    fun `a current cursor keeps the strictly-newer rule`() = runTest {
        val local = FakeLocal().apply {
            tasks["s1"] = task("s1")
            savedCursor = "1970-01-01 00:00:00.000Z"
        }
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("s1", savedAt = savedAt), "mac", "user-1")

        engine(remote, local).runSync(false)

        assertNull(local.tasks["s1"]!!.savedAt) // equal stamp: no-op outside a backfill
    }

    @Test
    fun `a failed backfill pull stamps nothing and pushes nothing`() = runTest {
        val local = FakeLocal().apply {
            tasks["s1"] = task("s1", updatedAt = now)
            savedCursor = PbDates.cursorAfter(pulledAt)
            contractVersion = 0
            queue += queueItem(1, task("s1", updatedAt = now))
        }
        val remote = FakeRemote()
        remote.listResponder = { _, _ -> throw PocketBaseApi.HttpError(500, "boom") }

        val result = engine(remote, local).runSync(false)

        assertEquals("error", result.status)
        assertEquals(0, local.contractVersion)
        assertEquals(1, local.queue.size)
        assertTrue(remote.records.isEmpty())
    }

    @Test
    fun `a fresh install stamps the contract after its first full pull`() = runTest {
        val local = FakeLocal().apply { contractVersion = 0 }
        engine(FakeRemote(), local).runSync(false)
        assertEquals(SyncEngine.PULL_CONTRACT_VERSION, local.contractVersion)
    }

    @Test
    fun `a change queued before the upgrade pushes the current row's placement, never an empty one`() = runTest {
        val edited = task("s1", updatedAt = now) // queued by 0.10.0: its payload has no savedAt
        val local = FakeLocal().apply {
            tasks["s1"] = edited.copy(savedAt = savedAt) // the backfill already merged it
            queue += queueItem(1, edited)
        }
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("s1", savedAt = savedAt), "mac", "user-1")

        engine(remote, local).runSync(false)

        assertEquals(PbDates.formatWire(savedAt), remote.records["r1"]!!.savedAt)
    }

    @Test
    fun `a queued change for a row that is gone leaves saved_at off the wire`() = runTest {
        val local = FakeLocal().apply {
            queue += queueItem(1, task("s1", updatedAt = now))
            tombstones["s1"] = now.toEpochMilli() // archived here since it was queued, so pull leaves it out
        }
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("s1", savedAt = savedAt), "mac", "user-1")

        engine(remote, local).runSync(false)

        val sent = remote.records["r1"]!!
        assertNull(sent.savedAt)
        assertFalse("saved_at" in WireJson.encode(sent))
    }

    @Test
    fun `a return to the matrix pushes an empty saved_at, which clears it on the server`() = runTest {
        val returned = task("s1", updatedAt = now)
        val local = FakeLocal().apply { tasks["s1"] = returned; queue += queueItem(1, returned) }
        val remote = FakeRemote()
        remote.records["r1"] = WireMapper.push(task("s1", savedAt = savedAt), "mac", "user-1")

        engine(remote, local).runSync(false)

        assertEquals("", remote.records["r1"]!!.savedAt)
    }

    @Test
    fun `the mapper reads saved_at, and an empty one means the matrix`() {
        val wire = WireMapper.push(task("s1", savedAt = savedAt), "mac", "user-1")
        assertEquals(savedAt, WireMapper.pull(wire, null)!!.savedAt)
        assertNull(WireMapper.pull(wire.copy(savedAt = ""), null)!!.savedAt)
        assertNull(WireMapper.pull(wire.copy(savedAt = null), null)!!.savedAt)
    }

    @Test
    fun `a return pulled from another device resets this device's reminder state`() {
        val local = task("s1", savedAt = savedAt).copy(
            notificationSent = true, lastNotificationAt = pulledAt, snoozedUntil = now,
        )
        val wire = WireMapper.push(task("s1", updatedAt = now), "mac", "user-1")
        val merged = WireMapper.pull(wire, local)!!
        assertNull(merged.savedAt)
        assertFalse(merged.notificationSent)
        assertNull(merged.lastNotificationAt)
        assertNull(merged.snoozedUntil)
    }
}
