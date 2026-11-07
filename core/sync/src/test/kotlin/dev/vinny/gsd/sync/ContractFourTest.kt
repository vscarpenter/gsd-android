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

/**
 * Pull contract 4 (spec-top-three.md §6), the Android rows of its named tests. The trap they pin: an
 * upgraded build must never push `focus_date: ""` for a pick an older build stored without the field.
 * Each test starts from the older build's state, as Sprint 1's S-AND-01 taught.
 */
class ContractFourTest {
    private val now = Instant.parse("2026-11-07T15:00:00.000Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val pulledAt = Instant.parse("2026-11-06T09:00:00.000Z") // when the older build last pulled
    private val edited = now.minusSeconds(600) // a local edit made on the older build, after that pull
    private val savedAt = Instant.parse("2026-11-01T12:00:00.000Z")
    private val farFuture = Instant.parse("2099-01-01T00:00:00.000Z")
    private val pickDate = "2026-11-07"

    private fun task(
        updatedAt: Instant = pulledAt,
        focusDate: String? = null,
        focusRank: Int? = null,
        savedAt: Instant? = null,
        title: String = "t",
    ) = Task(
        id = "pick-task-1", title = title, urgent = true, important = true,
        createdAt = pulledAt.minusSeconds(600), updatedAt = updatedAt, notifyBefore = 15,
        focusDate = focusDate, focusRank = focusRank, savedAt = savedAt,
    )

    private fun engine(remote: FakeRemote, local: FakeLocal) =
        SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { "user-1" })

    private fun queued(payload: Task, id: Long = 1) = SyncQueueEntity(
        id = id, taskId = payload.id, operation = SyncOp.UPDATE, timestamp = payload.updatedAt.toEpochMilli(),
        payloadJson = TaskJson.encode(payload),
    )

    /** The server's copy, picked on another device. Its `updated` sits at the older build's last pull. */
    private fun serverWith(row: Task, stampedAt: Instant = pulledAt) = FakeRemote().apply {
        records["r1"] = WireMapper.push(row, "other-device", "user-1", defaultReminder = 15)
        listResponder = { _, cursor ->
            val stamp = PbDates.formatSpace(stampedAt)
            val items = if (stamp >= cursor) records.entries.sortedBy { it.key }.map { it.value.copy(id = it.key) } else emptyList()
            TaskListPage(page = 1, totalPages = 1, items = items, updatedStamps = items.map { stamp })
        }
    }

    private fun pickedOnServer(updatedAt: Instant = pulledAt) = task(updatedAt, pickDate, 1)

    /** A device on contract 3 that pulled before the pick existed, so its cursor is past the server's row. */
    private fun upgraded(row: Task?, version: Int = 3, cursor: String? = PbDates.cursorAfter(pulledAt.plusSeconds(3_600))) =
        FakeLocal().apply {
            row?.let { tasks[it.id] = it }
            savedCursor = cursor
            contractVersion = version
        }

    private fun FakeRemote.pick(): Pair<String?, Any?> = records.getValue("r1").let { it.focusDate to it.focusRank?.content }

    @Test
    fun `backfill runs with a null cursor`() = runTest {
        val row = task(edited) // newer than the server, so only the backfill copy brings the pick
        val local = upgraded(row, cursor = null).apply { queue += queued(row) }
        val remote = serverWith(pickedOnServer())

        engine(remote, local).runSync(false)

        assertEquals(pickDate to 1, local.tasks.getValue(row.id).let { it.focusDate to it.focusRank })
        assertEquals(pickDate to "1", remote.pick())
        assertEquals(SyncEngine.PULL_CONTRACT_VERSION, local.contractVersion)
    }

    @Test
    fun `backfill pulls before it pushes`() = runTest {
        val row = task(edited)
        val local = upgraded(row).apply { queue += queued(row) }
        val remote = serverWith(pickedOnServer())

        engine(remote, local).runSync(false)

        assertEquals("list", remote.calls.first())
        assertTrue(remote.calls.indexOf("update") > remote.calls.indexOf("list"))
        assertEquals(pickDate to "1", remote.pick())
    }

    @Test
    fun `a failed backfill pull pushes nothing and stamps nothing`() = runTest {
        val row = task(edited)
        val local = upgraded(row).apply { queue += queued(row) }
        val remote = serverWith(pickedOnServer()).apply { listResponder = { _, _ -> throw PocketBaseApi.HttpError(500, "boom") } }

        val result = engine(remote, local).runSync(false)

        assertEquals("error", result.status)
        assertEquals(3, local.contractVersion)
        assertEquals(1, local.queue.size)
        assertEquals(pickDate to "1", remote.pick())
    }

    @Test
    fun `a locally newer row gets the server pick through the backfill`() = runTest {
        val row = task(edited, title = "renamed on the older build")
        val local = upgraded(row)

        val result = engine(serverWith(pickedOnServer()), local).runSync(false)

        assertEquals(row.copy(focusDate = pickDate, focusRank = 1), local.tasks[row.id]) // the pick, and nothing else
        assertEquals(0, result.conflicts)
    }

    /** On contract 3 a null savedAt is a real Return, maybe not pushed yet. Contract 4 mustn't un-return it. */
    @Test
    fun `a real return survives the contract 4 backfill`() = runTest {
        val returned = task(edited) // returned to the matrix on this contract 3 device, after the server's Save
        val local = upgraded(returned).apply { queue += queued(returned) }
        val remote = serverWith(task(savedAt = savedAt))

        engine(remote, local).runSync(false)

        assertNull(local.tasks.getValue(returned.id).savedAt)
        assertEquals("", remote.records.getValue("r1").savedAt)
    }

    @Test
    fun `an ordinary pull leaves a local un-pick alone`() = runTest {
        val unpicked = task(edited) // un-picked here on this contract 4 build; the push hasn't gone out
        val local = upgraded(unpicked, version = SyncEngine.PULL_CONTRACT_VERSION, cursor = PbDates.cursorAfter(pulledAt.minusSeconds(60)))
        val remote = serverWith(pickedOnServer())

        engine(remote, local).runSync(false)

        assertEquals(null to null, local.tasks.getValue(unpicked.id).let { it.focusDate to it.focusRank })
    }

    @Test
    fun `a queued payload from an older build keeps the server pick`() = runTest {
        val payload = task(edited) // queued by the older build, which never heard of the pick
        val current = payload.copy(focusDate = pickDate, focusRank = 1) // the row as this build now holds it
        val local = upgraded(current, version = SyncEngine.PULL_CONTRACT_VERSION).apply { queue += queued(payload) }
        val remote = serverWith(pickedOnServer())

        engine(remote, local).runSync(false)

        assertEquals(pickDate to "1", remote.pick())
    }

    @Test
    fun `a same-version row with a queued change keeps the server pick`() = runTest {
        val sameVersion = task(pulledAt) // the older build pulled this exact version, without the pick
        val local = upgraded(sameVersion).apply { queue += queued(sameVersion) }
        val remote = serverWith(pickedOnServer())

        val result = engine(remote, local).runSync(false)

        assertEquals(pickDate, local.tasks.getValue(sameVersion.id).focusDate)
        assertEquals(pickDate to "1", remote.pick())
        assertEquals(0, result.conflicts)
    }

    @Test
    fun `a queued item for a row that's gone omits both keys`() = runTest {
        val payload = task(edited) // archived or trashed here since it was queued
        val local = upgraded(null, version = SyncEngine.PULL_CONTRACT_VERSION).apply { queue += queued(payload) }
        val remote = serverWith(pickedOnServer())

        engine(remote, local).runSync(false)

        val sent = remote.records.getValue("r1")
        assertNull(sent.focusDate) // left off the request, so a real server keeps the pick
        assertNull(sent.focusRank)
    }

    @Test
    fun `the backfill leaves the queue empty when nothing changed`() = runTest {
        val local = upgraded(task(edited))

        engine(serverWith(pickedOnServer()), local).runSync(false)

        assertTrue(local.queue.isEmpty())
        assertEquals(edited, local.tasks.getValue("pick-task-1").updatedAt) // the copy is not an edit
    }

    /** Sprint question S-SPEC-01: a queued write holds off a far-future remote, and the copy still runs. */
    @Test
    fun `a far-future remote with a queued edit keeps the server pick through the backfill`() = runTest {
        val row = task(edited, title = "my edit")
        val local = upgraded(row).apply { queue += queued(row) }
        val remote = serverWith(pickedOnServer(updatedAt = farFuture))

        engine(remote, local).runSync(false)

        assertEquals("my edit", local.tasks.getValue(row.id).title) // the hold-off kept the edit here (S-AND-02)
        assertEquals(pickDate, local.tasks.getValue(row.id).focusDate)
        assertEquals(pickDate to "1", remote.pick())
        assertEquals("my edit", remote.records.getValue("r1").title) // and the local edit reached the server
    }
}
