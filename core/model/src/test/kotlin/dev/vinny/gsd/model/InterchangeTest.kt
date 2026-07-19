package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterchangeTest {
    private val t0 = Instant.parse("2026-06-15T08:30:00.500Z")

    private fun task(id: String = NanoId.generate(), title: String = "t", deps: List<String> = emptyList(), parent: String? = null) =
        Task(id = id, title = title, urgent = false, important = false,
            createdAt = t0, updatedAt = t0, dependencies = deps, parentTaskId = parent)

    @Test
    fun `export envelope has version, sorted keys, and round-trips`() {
        val raw = Interchange.export(listOf(task(title = "Ship")), exportedAt = t0)
        assertTrue("\"version\": 1" in raw)
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
        val raw = Interchange.export((1..3).map { task() }, exportedAt = t0)
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
