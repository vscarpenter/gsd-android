package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
    fun `export writes the 2_2_0 envelope with all seven stores`() {
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
        assertEquals("2.2.0", root.getValue("version").jsonPrimitive.content) // string, not int
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
        assertTrue("\"version\": \"2.2.0\"" in raw)
        assertTrue("\"exportedAt\": \"2026-06-15T08:30:00.500Z\"" in raw)
        // sorted keys: completed < createdAt < ... appear in order within a task object
        val completedIdx = raw.indexOf("\"completed\"")
        val createdIdx = raw.indexOf("\"createdAt\"")
        val titleIdx = raw.indexOf("\"title\"")
        assertTrue(completedIdx < createdIdx && createdIdx < titleIdx)

        val parsed = Interchange.parse(raw)
        assertEquals(1, parsed.envelope.tasks.size)
        assertEquals("Ship", parsed.envelope.tasks.single().title)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun `lenient parse skips invalid tasks and counts them`() {
        val raw = """{"tasks":[{"id":"good","title":"ok","urgent":false,"important":false,
            "createdAt":"2026-06-15T08:30:00.500Z","updatedAt":"2026-06-15T08:30:00.500Z"},
            {"title":"missing id"}],"exportedAt":"2026-06-15T08:30:00.500Z","version":1}"""
        val parsed = Interchange.parse(raw)
        assertEquals(1, parsed.envelope.tasks.size)
        assertEquals(1, parsed.skipped)
    }

    @Test
    fun `parse accepts int, string, and absent versions`() {
        assertEquals("1", Interchange.parse("""{"tasks": [], "version": 1, "exportedAt": "2026-01-01T00:00:00.000Z"}""").version)
        assertEquals("2.1.0", Interchange.parse("""{"tasks": [], "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""").version)
        assertEquals("1", Interchange.parse("""{"tasks": []}""").version) // absent → "1" (iOS-style leniency, P§2.2)
    }

    @Test
    fun `cap counts all three task-bearing arrays`() {
        fun records(n: Int, prefix: String) = (1..n).joinToString(",") {
            """{"id":"$prefix-${it.toString().padStart(6, '0')}","title":"t","urgent":false,"important":false,
                "createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z"}"""
        }
        val overCap = """{"tasks":[${records(4000, "a")}],"archivedTasks":[${records(3001, "b")}],
            "deletedTasks":[${records(3000, "c")}],"version":"2.1.0","exportedAt":"2026-01-01T00:00:00.000Z"}"""
        assertFailsWith<Interchange.TooLarge> { Interchange.parse(overCap) } // 10,001
    }

    @Test
    fun `absent stores decode to null, present-empty to empty lists`() {
        val absent = Interchange.parse("""{"tasks": [], "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
        assertNull(absent.envelope.archivedTasks)
        val present = Interchange.parse("""{"tasks": [], "archivedTasks": [], "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
        assertEquals(emptyList(), present.envelope.archivedTasks)
    }

    @Test
    fun `incoming quadrant is ignored and recomputed from the flags`() {
        val parsed = Interchange.parse("""{"tasks":[{"id":"task-1","title":"t","urgent":true,"important":true,
            "quadrant":"not-urgent-not-important",
            "createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z"}],
            "version":"2.1.0","exportedAt":"2026-01-01T00:00:00.000Z"}""")
        assertEquals(Quadrant.DO_FIRST, parsed.envelope.tasks.single().quadrant)
    }

    @Test
    fun `archived and deleted rows carry their timestamps, defaulting to now when unparseable`() {
        val parsed = Interchange.parse("""{"tasks":[],
            "archivedTasks":[{"id":"arch-1","title":"t","urgent":false,"important":false,
                "createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z",
                "archivedAt":"2026-06-01T10:00:00.000Z"}],
            "deletedTasks":[{"id":"tr-1","title":"t","urgent":false,"important":false,
                "createdAt":"2026-01-01T00:00:00.000Z","updatedAt":"2026-01-01T00:00:00.000Z",
                "deletedAt":"garbage"}],
            "version":"2.1.0","exportedAt":"2026-01-01T00:00:00.000Z"}""")
        assertEquals(Instant.parse("2026-06-01T10:00:00.000Z"), parsed.envelope.archivedTasks!!.single().archivedAt)
        // unparseable deletedAt: the row imports anyway; timestamp defaulted (P§2.2) — assert non-null, not a value
        assertNotNull(parsed.envelope.deletedTasks!!.single().deletedAt)
    }

    @Test
    fun `a valid custom smart view decodes with its FilterCriteria intact`() {
        val parsed = Interchange.parse("""{"tasks": [],
            "smartViews": [{"id": "view-1", "name": "Overdue work", "icon": "overdue",
                "criteria": {"overdue": true, "status": "active"},
                "isBuiltIn": false,
                "createdAt": "2026-07-01T09:00:00.000Z", "updatedAt": "2026-07-15T09:00:00.000Z"}],
            "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
        val view = parsed.envelope.smartViews!!.single()
        assertEquals("view-1", view.id)
        assertEquals("Overdue work", view.name)
        assertEquals("overdue", view.icon)
        assertEquals(false, view.isBuiltIn)
        assertEquals(true, view.criteria.overdue)
        assertEquals(FilterStatus.ACTIVE, view.criteria.status)
    }

    @Test
    fun `a smart view row with isBuiltIn true is dropped`() {
        val parsed = Interchange.parse("""{"tasks": [],
            "smartViews": [{"id": "today-focus", "name": "Today's Focus", "icon": "adjust",
                "criteria": {}, "isBuiltIn": true,
                "createdAt": "2026-01-01T00:00:00.000Z", "updatedAt": "2026-01-01T00:00:00.000Z"}],
            "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
        assertEquals(emptyList(), parsed.envelope.smartViews) // present-and-dropped, not absent
    }

    @Test
    fun `a smart view row with invalid criteria is dropped`() {
        val parsed = Interchange.parse("""{"tasks": [],
            "smartViews": [{"id": "view-2", "name": "Bad", "icon": "warning",
                "criteria": {"status": "not-a-real-status"}, "isBuiltIn": false,
                "createdAt": "2026-01-01T00:00:00.000Z", "updatedAt": "2026-01-01T00:00:00.000Z"}],
            "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
        assertEquals(emptyList(), parsed.envelope.smartViews)
    }

    @Test
    fun `all three singletons decode from a well-formed payload`() {
        val parsed = Interchange.parse("""{"tasks": [],
            "notificationSettings": {"id": "settings", "enabled": true, "defaultReminder": 15,
                "soundEnabled": true, "quietHoursStart": "22:00", "quietHoursEnd": "07:00",
                "permissionAsked": false, "updatedAt": "2026-08-18T12:00:00.000Z"},
            "archiveSettings": {"id": "settings", "enabled": false, "archiveAfterDays": 30},
            "appPreferences": {"id": "preferences", "pinnedSmartViewIds": ["view-1"],
                "maxPinnedViews": 5, "smartViewsEnabled": true},
            "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
        val notif = parsed.envelope.notificationSettings!!
        assertEquals(true, notif.enabled)
        assertEquals(15, notif.defaultReminder)
        assertEquals(true, notif.soundEnabled)
        assertEquals("22:00", notif.quietHoursStart)
        assertEquals("07:00", notif.quietHoursEnd)
        assertEquals(false, notif.permissionAsked)
        val archive = parsed.envelope.archiveSettings!!
        assertEquals(false, archive.enabled)
        assertEquals(30, archive.archiveAfterDays)
        val prefs = parsed.envelope.appPreferences!!
        assertEquals(listOf("view-1"), prefs.pinnedSmartViewIds)

        // quiet-hours: absent keys decode to null, not the sentinel-string "null"
        val withoutQuietHours = Interchange.parse("""{"tasks": [],
            "notificationSettings": {"id": "settings", "enabled": false, "defaultReminder": 30,
                "soundEnabled": false, "permissionAsked": true, "updatedAt": "2026-08-18T12:00:00.000Z"},
            "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""").envelope.notificationSettings!!
        assertNull(withoutQuietHours.quietHoursStart)
        assertNull(withoutQuietHours.quietHoursEnd)
    }

    /** Review, "Also worth a look": an unparseable quiet-hours time came in as is and threw on every read. */
    @Test
    fun `an unparseable quiet-hours time turns quiet hours off and keeps the rest`() {
        val notif = Interchange.parse("""{"tasks": [],
            "notificationSettings": {"id": "settings", "enabled": true, "defaultReminder": 30,
                "soundEnabled": true, "quietHoursStart": "25:00", "quietHoursEnd": "07:00",
                "permissionAsked": false, "updatedAt": "2026-08-18T12:00:00.000Z"},
            "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""").envelope.notificationSettings!!
        assertNull(notif.quietHoursStart)
        assertNull(notif.quietHoursEnd)
        assertEquals(30, notif.defaultReminder)
    }

    /**
     * The cap admits its limit: 10,000 rows get past it at the default, and one more is refused. The
     * cap counts rows before any decode, so empty rows keep this fast; each is then skipped and counted.
     */
    @Test
    fun `the default cap admits exactly ten thousand tasks`() {
        fun payload(n: Int) = List(n) { "{}" }.joinToString(",", prefix = "{\"tasks\": [", postfix = "], \"version\": \"2.1.0\"}")
        assertEquals(10_000, Interchange.parse(payload(10_000)).skipped)
        assertTrue(runCatching { Interchange.parse(payload(10_001)) }.exceptionOrNull() is Interchange.TooLarge)
    }

    @Test
    fun `a malformed singleton decodes to null, treated as absent`() {
        val parsed = Interchange.parse("""{"tasks": [],
            "archiveSettings": {"enabled": false},
            "version": "2.1.0", "exportedAt": "2026-01-01T00:00:00.000Z"}""")
        assertNull(parsed.envelope.archiveSettings) // missing archiveAfterDays — fails to decode, not a crash
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

    @Test
    fun `a smart view over a bound is skipped and counted`() {
        val longName = "n".repeat(81)
        val raw = """{"version":"2.1.0","tasks":[],"smartViews":[
            {"id":"ok","name":"Fine","icon":"bolt","isBuiltIn":false,"criteria":{},
             "createdAt":"2026-07-19T12:00:00.000Z","updatedAt":"2026-07-19T12:00:00.000Z"},
            {"id":"bad","name":"$longName","icon":"bolt","isBuiltIn":false,"criteria":{},
             "createdAt":"2026-07-19T12:00:00.000Z","updatedAt":"2026-07-19T12:00:00.000Z"}]}"""
        val import = Interchange.parse(raw)
        assertEquals(listOf("ok"), import.envelope.smartViews?.map { it.id })
        assertEquals(1, import.skipped)
    }

    @Test
    fun `a malformed smart view row now counts as skipped`() {
        val raw = """{"version":"2.1.0","tasks":[],"smartViews":[{"id":"x"}]}"""
        assertEquals(1, Interchange.parse(raw).skipped)
    }

    @Test
    fun `pinned ids are truncated to five valid entries on import`() {
        val ids = (1..7).map { "\"v$it\"" } + "\"\"" + "\"" + "x".repeat(129) + "\""
        val raw = """{"version":"2.1.0","tasks":[],"appPreferences":{"id":"preferences","pinnedSmartViewIds":[${ids.joinToString(",")}]}}"""
        assertEquals(listOf("v1", "v2", "v3", "v4", "v5"), Interchange.parse(raw).envelope.appPreferences?.pinnedSmartViewIds)
    }

    @Test
    fun `export clips smart views at 100 in createdAt then id order`() {
        val base = Instant.parse("2026-07-19T12:00:00.000Z")
        val views = (0 until 105).map { i ->
            SmartView("v${105 - i}", "V$i", "bolt", FilterCriteria(), false, base.plusSeconds((i % 3).toLong()), base)
        }
        val json = Interchange.export(BackupEnvelope(tasks = emptyList(), smartViews = views), exportedAt = base)
        val emitted = Json.parseToJsonElement(json).jsonObject.getValue("smartViews").jsonArray
        assertEquals(100, emitted.size)
        val firstThree = emitted.take(3).map { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(listOf("v102", "v105", "v12"), firstThree)
        val emittedIds = emitted.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
        listOf("v85", "v88", "v91", "v94", "v97").forEach { assertTrue(it !in emittedIds) }
    }
}
