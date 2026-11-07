package dev.vinny.gsd.sync

import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeEntry
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WireMapperTest {
    private val t0 = Instant.parse("2026-06-15T08:30:00.500Z")

    private fun task(
        timeEntries: List<TimeEntry> = emptyList(),
        snoozedUntil: Instant? = null,
        estimatedMinutes: Int? = null,
    ) = Task(
        id = "task-abc-123456789012", title = "Ship", description = "notes",
        urgent = true, important = false, completed = false,
        createdAt = t0.minusSeconds(60), updatedAt = t0,
        dueDate = t0.plusSeconds(3600), recurrence = Recurrence.WEEKLY,
        tags = listOf("work"), subtasks = listOf(Subtask("sub1", "step", true)),
        dependencies = listOf("dep-1"), parentTaskId = "parent-123",
        notifyBefore = 30, notificationSent = true, lastNotificationAt = t0,
        snoozedUntil = snoozedUntil, estimatedMinutes = estimatedMinutes,
        timeEntries = timeEntries,
    )

    @Test
    fun `push maps to snake_case with lossy flattened time entries`() {
        val entries = listOf(
            TimeEntry("entry001", t0, t0.plusSeconds(150), notes = "secret note"), // 2 min
            TimeEntry("entry002", t0, null), // running -> 0
        )
        val wire = WireMapper.push(task(timeEntries = entries), deviceId = "device-1", owner = "user-1", defaultReminder = 15)
        assertEquals("task-abc-123456789012", wire.taskId)
        assertEquals("user-1", wire.owner) // required by the collection rule — blank 400s (field: owner)
        assertEquals("urgent-not-important", wire.quadrant)
        assertEquals("2026-06-15T08:30:00.500Z", wire.clientUpdatedAt)
        assertEquals("2026-06-15T08:29:00.500Z", wire.clientCreatedAt)
        assertEquals("device-1", wire.deviceId)
        assertEquals(2, wire.timeEntries.size)
        assertEquals(2, wire.timeEntries[0].minutes)
        assertEquals(0, wire.timeEntries[1].minutes)
        assertEquals(2, wire.timeSpent)
        // wire snooze is included on push (device-local semantics apply on PULL)
        assertEquals("", wire.snoozedUntil)
    }

    /** D-10: PocketBase stores a null or missing number as 0, which means "at the due time". */
    @Test
    fun `the wire never carries a null offset`() {
        val legacy = WireMapper.push(task().copy(notifyBefore = null), deviceId = "d", owner = "user-1", defaultReminder = 45)
        assertEquals(45, legacy.notifyBefore)
        val obj = Json.parseToJsonElement(WireJson.encode(legacy)).jsonObject
        assertEquals("45", obj["notify_before"].toString())
        val numbered = WireMapper.push(task(), deviceId = "d", owner = "user-1", defaultReminder = 45)
        assertEquals(30, numbered.notifyBefore) // a stored number wins over the default
    }

    @Test
    fun `push serializes with exact wire field names and empty-string nil dates`() {
        val raw = WireJson.encode(WireMapper.push(task(), deviceId = "d", owner = "user-1", defaultReminder = 15))
        val obj = Json.parseToJsonElement(raw).jsonObject
        assertTrue("task_id" in obj)
        assertTrue("client_updated_at" in obj)
        assertTrue("notification_enabled" in obj)
        assertEquals("\"\"", obj["completed_at"].toString()) // nil date -> ""
        // camelCase startedAt survives inside the otherwise snake_case record
        assertTrue("startedAt" in WireJson.encode(
            WireMapper.push(task(timeEntries = listOf(TimeEntry("e1", t0, t0))), deviceId = "d", owner = "user-1", defaultReminder = 15),
        ))
        assertTrue("\"id\":\"\"" in raw.replace(" ", "")) // record id empty on create
    }

    @Test
    fun `pull with no local copy reconstructs rich entries from minutes`() {
        val wire = WireMapper.push(
            task(timeEntries = listOf(TimeEntry("entry001", t0, t0.plusSeconds(120)))),
            deviceId = "other", owner = "user-1", defaultReminder = 15,
        )
        val pulled = WireMapper.pull(wire, local = null)!!
        assertEquals("task-abc-123456789012", pulled.id)
        val entry = pulled.timeEntries.single()
        assertEquals(t0, entry.startedAt)
        assertEquals(t0.plusSeconds(120), entry.endedAt)
        assertNull(entry.notes)
        assertNull(pulled.parentTaskId) // device-local, not on the wire
        assertEquals(false, pulled.notificationSent)
    }

    @Test
    fun `pull with local copy preserves device-local fields and rich entries`() {
        val richLocal = task(
            timeEntries = listOf(TimeEntry("entry001", t0, t0.plusSeconds(120), notes = "keep me")),
            snoozedUntil = t0.plusSeconds(9999),
        )
        val remote = WireMapper.push(task().copy(title = "Renamed remotely"), deviceId = "other", owner = "user-1", defaultReminder = 15)
        val merged = WireMapper.pull(remote, local = richLocal)!!
        assertEquals("Renamed remotely", merged.title) // remote wins synced fields
        assertEquals("parent-123", merged.parentTaskId) // preserved
        assertEquals(true, merged.notificationSent) // preserved
        assertEquals(t0, merged.lastNotificationAt) // preserved
        assertEquals(t0.plusSeconds(9999), merged.snoozedUntil) // preserved
        assertEquals("keep me", merged.timeEntries.single().notes) // rich local copy preferred
    }

    @Test
    fun `pull is lenient - only task_id required, bad client_updated_at skips`() {
        val minimal = WireJson.decodeOrNull("""{"task_id":"tiny","client_updated_at":"2026-06-15 08:30:00.500Z"}""")!!
        val pulled = WireMapper.pull(minimal, local = null)!!
        assertEquals("tiny", pulled.id)
        assertEquals("", pulled.title.ifEmpty { "" })
        assertEquals(Recurrence.NONE, pulled.recurrence)

        assertNull(WireJson.decodeOrNull("""{"title":"no task_id"}"""))
        val badStamp = WireJson.decodeOrNull("""{"task_id":"x","client_updated_at":"garbage"}""")!!
        assertNull(WireMapper.pull(badStamp, local = null)) // unparseable stamp -> skip record
    }

    @Test
    fun `estimated_minutes zero means unset and quadrant is recomputed from flags`() {
        val raw = """{"task_id":"abcd","client_updated_at":"2026-06-15T08:30:00.500Z",
            "urgent":false,"important":true,"quadrant":"urgent-important","estimated_minutes":0}"""
        val pulled = WireMapper.pull(WireJson.decodeOrNull(raw)!!, local = null)!!
        assertNull(pulled.estimatedMinutes)
        assertEquals("not-urgent-important", pulled.quadrant.id) // flags win over wire quadrant
    }

    /**
     * Parity backlog B-55 (web #669): another device moved the due date, so the reminder this device
     * already showed was for the old time. A new due date is a new reminder, as a local edit makes it.
     */
    @Test
    fun `pull that moves the due date resets this device's reminder state`() {
        val local = task(snoozedUntil = t0.plusSeconds(600))
        val moved = WireMapper.push(local.copy(dueDate = t0.plusSeconds(7200)), deviceId = "other", owner = "user-1", defaultReminder = 15)

        val pulled = WireMapper.pull(moved, local = local)!!

        assertEquals(false, pulled.notificationSent)
        assertNull(pulled.lastNotificationAt)
        assertNull(pulled.snoozedUntil)
    }

    @Test
    fun `pull that clears the due date resets this device's reminder state too`() {
        val local = task(snoozedUntil = t0.plusSeconds(600))
        val cleared = WireMapper.push(local.copy(dueDate = null), deviceId = "other", owner = "user-1", defaultReminder = 15)

        val pulled = WireMapper.pull(cleared, local = local)!!

        assertEquals(false, pulled.notificationSent)
        assertNull(pulled.lastNotificationAt)
        assertNull(pulled.snoozedUntil)
    }

    // --- top three (spec-top-three.md §5, §10, C4-6) ---

    private fun encoded(wire: WireTask) = Json.parseToJsonElement(WireJson.encode(wire)).jsonObject

    @Test
    fun `push writes the pick, and an unset pair as an empty date and rank 0`() {
        val picked = encoded(WireMapper.push(task().copy(focusDate = "2026-11-07", focusRank = 2), "d", "user-1", defaultReminder = 15))
        assertEquals("\"2026-11-07\"", picked["focus_date"].toString())
        assertEquals("2", picked["focus_rank"].toString()) // an integer: the server's field is onlyInt
        val unset = encoded(WireMapper.push(task(), "d", "user-1", defaultReminder = 15))
        assertEquals("\"\"", unset["focus_date"].toString())
        assertEquals("0", unset["focus_rank"].toString())
    }

    /** C4-6: with no current row, the server keeps its pick and its placement. */
    @Test
    fun `push leaves the pick and placement off when it can't read the current row`() {
        val picked = task().copy(focusDate = "2026-11-07", focusRank = 2)
        val obj = encoded(WireMapper.push(picked, "d", "user-1", defaultReminder = 15, hasCurrentRow = false))
        assertFalse("focus_date" in obj || "focus_rank" in obj || "saved_at" in obj)
    }

    /** Section 3.5: a pulled pair counts only as a real date with an integer rank from 1 to 3. */
    @Test
    fun `pull keeps only a real date with a rank from 1 to 3`() {
        fun pulled(fields: String) = WireMapper.pull(
            WireJson.decodeOrNull("""{"task_id":"pick-1","client_updated_at":"2026-11-07T12:00:00.000Z",$fields}""")!!,
            local = null,
        )!!.let { it.focusDate to it.focusRank }
        assertEquals("2026-11-07" to 1, pulled(""""focus_date":"2026-11-07","focus_rank":1"""))
        listOf(
            """"focus_date":"","focus_rank":0""",
            """"focus_date":"2026-02-30","focus_rank":1""",
            """"focus_date":"2026-11-07","focus_rank":1.5""",
            """"focus_date":"2026-11-07","focus_rank":"1"""",
            """"focus_date":"2026-11-07","focus_rank":4""",
            """"title":"from a server without the fields"""",
        ).forEach { assertEquals(null to null, pulled(it), it) }
    }
}
