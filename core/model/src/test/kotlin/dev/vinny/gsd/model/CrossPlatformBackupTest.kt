package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Both suites' real-export fixtures, imported by the third client (P§2.3).
 * Same-client round-trips are a mirror, not a check — these files are what
 * caught three real mismatches between web and iOS within minutes.
 */
class CrossPlatformBackupTest {
    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/cross-platform/$name"),
    ).readBytes().decodeToString()

    @Test
    fun `the iOS export imports losslessly`() {
        val parsed = Interchange.parse(fixture("ios-backup-export.json"))
        assertEquals(0, parsed.skipped)
        assertEquals("2.1.0", parsed.version)
        assertEquals(2, parsed.envelope.tasks.size)
        assertEquals(1, parsed.envelope.archivedTasks!!.size)
        assertEquals(1, parsed.envelope.deletedTasks!!.size)
        assertEquals(1, parsed.envelope.smartViews!!.size)
        val rich = parsed.envelope.tasks.first { it.id == "web-task-1" }
        assertEquals(Quadrant.DO_FIRST, rich.quadrant)                 // recomputed, not read
        assertEquals(2, rich.subtasks.size)
        assertEquals(1, rich.timeEntries.size)                         // rich entries survive
        assertEquals(Instant.parse("2026-08-27T09:00:00.000Z"),
            parsed.envelope.deletedTasks!!.single().deletedAt)
        assertEquals("22:00", parsed.envelope.notificationSettings!!.quietHoursStart)
        assertEquals(60, parsed.envelope.archiveSettings!!.archiveAfterDays)
        assertEquals(listOf("custom-view-1"), parsed.envelope.appPreferences!!.pinnedSmartViewIds)
    }

    @Test
    fun `the web export imports losslessly`() {
        val parsed = Interchange.parse(fixture("web-backup-2.1.0.json"))
        assertEquals(0, parsed.skipped)
        assertEquals("2.1.0", parsed.version)
        assertEquals(2, parsed.envelope.tasks.size)
        assertEquals(1, parsed.envelope.archivedTasks!!.size)
        assertEquals(1, parsed.envelope.deletedTasks!!.size)
        assertEquals(1, parsed.envelope.smartViews!!.size)
        val rich = parsed.envelope.tasks.first { it.id == "web-task-1" }
        assertEquals(Quadrant.DO_FIRST, rich.quadrant)
        assertEquals(2, rich.subtasks.size)
        assertEquals(1, rich.timeEntries.size)
        assertEquals(Instant.parse("2026-08-27T09:00:00.000Z"),
            parsed.envelope.deletedTasks!!.single().deletedAt)
        // web's smartView row carries an extra top-level "description" key Android's
        // SmartView model doesn't have — lenient decode ignores it (dropped on
        // re-export is a documented loss, not a bug, per P§2.1).
        val view = parsed.envelope.smartViews!!.single()
        assertEquals("custom-view-1", view.id)
        assertEquals("Overdue work", view.name)
        assertEquals(listOf("work"), view.criteria.tags)
        assertEquals(listOf("urgent-important"), view.criteria.quadrants)
        assertEquals("22:00", parsed.envelope.notificationSettings!!.quietHoursStart)
        assertEquals(60, parsed.envelope.archiveSettings!!.archiveAfterDays)
        assertEquals(listOf("custom-view-1"), parsed.envelope.appPreferences!!.pinnedSmartViewIds)
    }

    @Test
    fun `an Android re-export of the iOS fixture reparses to the same envelope`() {
        val first = Interchange.parse(fixture("ios-backup-export.json"))
        val reExported = Interchange.export(first.envelope, Instant.parse("2026-08-29T12:00:00.000Z"))
        val second = Interchange.parse(reExported)
        assertEquals(first.envelope, second.envelope)                  // content-stable round trip
        val root = Json.parseToJsonElement(reExported).jsonObject
        assertEquals("2.1.0", root.getValue("version").jsonPrimitive.content)
    }
}
