package dev.vinny.gsd.model

import java.time.Instant
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Backup envelope 2.2.0 (spec-top-three.md §7). The fixture is the web's real export, byte-identical in
 * all three repos (md5 6da5aec65096d917c777e7a51a8d925e, frozen by the Lead on 2026-11-07).
 */
class TopThreeBackupTest {
    private val t0 = Instant.parse("2026-11-07T12:00:00.000Z")

    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/cross-platform/$name"),
    ).readBytes().decodeToString()

    private fun Task.pick(): Pair<String?, Int?> = focusDate to focusRank

    private fun task(id: String, focusDate: String? = null, focusRank: Int? = null) = Task(
        id = id, title = id, urgent = true, important = true, createdAt = t0, updatedAt = t0,
        focusDate = focusDate, focusRank = focusRank,
    )

    @Test
    fun `the web 2_2_0 export imports with its picks`() {
        val parsed = Interchange.parse(fixture("web-top-three-2.2.0.json"))

        assertEquals("2.2.0", parsed.version)
        assertEquals(0, parsed.skipped)
        assertEquals(
            listOf(null to null, "2026-11-07" to 2, "2026-11-07" to 1),
            parsed.envelope.tasks.map { it.pick() },
        )
        assertEquals(listOf("2026-11-06" to 1), parsed.envelope.archivedTasks!!.map { it.task.pick() })
        assertEquals(listOf("2026-11-07" to 3), parsed.envelope.deletedTasks!!.map { it.task.pick() })
    }

    /** Spec §12.4: backup and restore keep a pick, and an unset pair writes neither key. */
    @Test
    fun `an export writes 2_2_0 and a pick round-trips`() {
        val envelope = BackupEnvelope(
            tasks = listOf(task("picked-task-01", "2026-11-07", 1), task("unpicked-task1")),
            archivedTasks = listOf(ArchivedTask(task("archived-task1", "2026-11-06", 2), t0)),
            deletedTasks = listOf(DeletedTask(task("deleted-task01", "2026-11-05", 3), t0)),
        )

        val raw = Interchange.export(envelope, t0)
        val root = Json.parseToJsonElement(raw).jsonObject
        val parsed = Interchange.parse(raw)

        assertEquals("2.2.0", root.getValue("version").jsonPrimitive.content)
        val unpicked = root.getValue("tasks").jsonArray[1].jsonObject
        assertFalse("focusDate" in unpicked || "focusRank" in unpicked, "an unset pair is omitted")
        assertEquals(envelope.tasks, parsed.envelope.tasks)
        assertEquals(envelope.archivedTasks, parsed.envelope.archivedTasks)
        assertEquals(envelope.deletedTasks, parsed.envelope.deletedTasks)
    }

    /** Spec §7 and §3.5: a bad pick must not refuse a whole backup, or even one task. */
    @Test
    fun `an invalid pair becomes unset and keeps the task`() {
        val bad = listOf(
            """"focusDate": "2026-02-30", "focusRank": 1""", // no such day
            """"focusDate": "2026-11-7", "focusRank": 1""", // not YYYY-MM-DD
            """"focusDate": "2026-11-07", "focusRank": 4""", // past the third slot
            """"focusDate": "2026-11-07", "focusRank": 2.5""", // not an integer
            """"focusDate": "2026-11-07", "focusRank": "1"""", // not a number
            """"focusDate": "2026-11-07"""", // half a pair
            """"focusRank": 2""",
        )
        val rows = bad.mapIndexed { index, pair ->
            """{"id": "bad-pick-$index", "title": "kept", "urgent": true, "important": true,
               "createdAt": "2026-11-07T12:00:00.000Z", "updatedAt": "2026-11-07T12:00:00.000Z", $pair}"""
        }
        val raw = """{"version": "2.2.0", "tasks": [${rows.joinToString(",")}]}"""

        val parsed = Interchange.parse(raw)

        assertEquals(0, parsed.skipped)
        assertEquals(bad.size, parsed.envelope.tasks.size)
        parsed.envelope.tasks.forEachIndexed { index, task ->
            assertEquals(null to null, task.pick(), "case $index: ${bad[index]}")
        }
    }
}
