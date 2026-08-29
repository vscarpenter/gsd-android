package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class InterchangeTest {
    private val t0 = Instant.parse("2026-06-15T08:30:00.500Z")

    private fun task(id: String = NanoId.generate(), title: String = "t", deps: List<String> = emptyList(), parent: String? = null) =
        Task(id = id, title = title, urgent = false, important = false,
            createdAt = t0, updatedAt = t0, dependencies = deps, parentTaskId = parent)

    @Test
    fun `export writes the 2_1_0 envelope with all seven stores`() {
        val t = task(id = "task-000000000000000001")
        val out = Interchange.export(
            BackupEnvelope(
                tasks = listOf(t),
                archivedTasks = listOf(ArchivedTask(t.copy(id = "arch-00000000000000001", completed = true), Instant.parse("2026-06-01T10:00:00.000Z"))),
                deletedTasks = listOf(DeletedTask(t.copy(id = "trash-0000000000000001"), Instant.parse("2026-08-27T09:00:00.000Z"))),
                smartViews = listOf(SmartView("view-00000001", "Overdue work", "overdue", FilterCriteria(overdue = true), false, Instant.parse("2026-07-01T09:00:00.000Z"), Instant.parse("2026-07-15T09:00:00.000Z"))),
                notificationSettings = BackupNotificationSettings(true, 15, true, "22:00", "07:00", false, Instant.parse("2026-08-18T12:00:00.000Z")),
                archiveSettings = BackupArchiveSettings(enabled = false, archiveAfterDays = 30),
                appPreferences = BackupAppPreferences(listOf("view-00000001")),
            ),
            exportedAt = Instant.parse("2026-08-29T12:00:00.000Z"),
        )
        val root = Json.parseToJsonElement(out).jsonObject
        assertEquals("2.1.0", root.getValue("version").jsonPrimitive.content) // string, not int
        assertEquals(
            listOf("appPreferences", "archiveSettings", "archivedTasks", "deletedTasks",
                "exportedAt", "notificationSettings", "smartViews", "tasks", "version"),
            root.keys.toList(), // sorted keys
        )
        val taskRecord = root.getValue("tasks").jsonArray[0].jsonObject
        assertEquals(t.quadrant.id, taskRecord.getValue("quadrant").jsonPrimitive.content) // quadrant written
        assertEquals("2026-06-01T10:00:00.000Z",
            root.getValue("archivedTasks").jsonArray[0].jsonObject.getValue("archivedAt").jsonPrimitive.content)
        assertEquals("2026-08-27T09:00:00.000Z",
            root.getValue("deletedTasks").jsonArray[0].jsonObject.getValue("deletedAt").jsonPrimitive.content)
        val prefs = root.getValue("appPreferences").jsonObject
        assertEquals("preferences", prefs.getValue("id").jsonPrimitive.content)
        assertEquals(5, prefs.getValue("maxPinnedViews").jsonPrimitive.int) // web-schema constants
        assertEquals(true, prefs.getValue("smartViewsEnabled").jsonPrimitive.boolean)
        val notif = root.getValue("notificationSettings").jsonObject
        assertEquals("settings", notif.getValue("id").jsonPrimitive.content)
        assertEquals("22:00", notif.getValue("quietHoursStart").jsonPrimitive.content)
    }

    @Test
    fun `absent stores are omitted keys, not empty arrays`() {
        val out = Interchange.export(BackupEnvelope(tasks = emptyList()), Instant.parse("2026-08-29T12:00:00.000Z"))
        val root = Json.parseToJsonElement(out).jsonObject
        assertEquals(setOf("exportedAt", "tasks", "version"), root.keys)
    }

    @Test
    fun `export envelope has version, sorted keys, and round-trips`() {
        val raw = Interchange.export(BackupEnvelope(tasks = listOf(task(title = "Ship"))), exportedAt = t0)
        assertTrue("\"version\": \"2.1.0\"" in raw)
        assertTrue("\"exportedAt\": \"2026-06-15T08:30:00.500Z\"" in raw)
        // sorted keys: completed < createdAt < ... appear in order within a task object
        val completedIdx = raw.indexOf("\"completed\"")
        val createdIdx = raw.indexOf("\"createdAt\"")
        val titleIdx = raw.indexOf("\"title\"")
        assertTrue(completedIdx < createdIdx && createdIdx < titleIdx)

        val parsed = Interchange.parse(raw)
        assertEquals(1, parsed.tasks.size)
        assertEquals("Ship", parsed.tasks.single().title)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun `lenient parse skips invalid tasks and counts them`() {
        val raw = """{"tasks":[{"id":"good","title":"ok","urgent":false,"important":false,
            "createdAt":"2026-06-15T08:30:00.500Z","updatedAt":"2026-06-15T08:30:00.500Z"},
            {"title":"missing id"}],"exportedAt":"2026-06-15T08:30:00.500Z","version":1}"""
        val parsed = Interchange.parse(raw)
        assertEquals(1, parsed.tasks.size)
        assertEquals(1, parsed.skipped)
    }

    @Test
    fun `size and count caps enforced`() {
        val raw = Interchange.export(BackupEnvelope(tasks = (1..3).map { task() }), exportedAt = t0)
        assertTrue(runCatching { Interchange.parse(raw, limitTasks = 2) }.isFailure)
        assertTrue(runCatching { Interchange.parse(raw, limitBytes = 10) }.isFailure)
    }

    @Test
    fun `merge regenerates colliding ids and remaps deps including forward references`() {
        val existing = listOf(task(id = "collide-id-000000000"))
        // imported: B references A (forward reference: A appears after B in the list)
        val importedA = task(id = "collide-id-000000000", title = "A")
        val importedB = task(id = "imported-b-000000000", title = "B",
            deps = listOf("collide-id-000000000"), parent = "collide-id-000000000")
        val merged = MergePlan.compute(existing, listOf(importedB, importedA))
        val newA = merged.single { it.title == "A" }
        val newB = merged.single { it.title == "B" }
        assertTrue(newA.id != "collide-id-000000000")
        assertEquals(listOf(newA.id), newB.dependencies)
        assertEquals(newA.id, newB.parentTaskId)
    }
}
