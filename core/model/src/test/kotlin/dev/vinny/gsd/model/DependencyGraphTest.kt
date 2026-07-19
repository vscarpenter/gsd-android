package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DependencyGraphTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(id: String, deps: List<String> = emptyList(), completed: Boolean = false) =
        Task(
            id = id, title = id, urgent = false, important = false, completed = completed,
            createdAt = t0, updatedAt = t0, dependencies = deps,
        )

    @Test
    fun `self-reference is always a cycle`() {
        assertTrue(DependencyGraph.wouldCreateCycle(listOf(task("aaaa")), "aaaa", "aaaa"))
    }

    @Test
    fun `direct cycle rejected`() {
        // B depends on A; adding A→B closes the loop
        val tasks = listOf(task("aaaa"), task("bbbb", deps = listOf("aaaa")))
        assertTrue(DependencyGraph.wouldCreateCycle(tasks, "aaaa", "bbbb"))
    }

    @Test
    fun `transitive cycle rejected`() {
        // C→B→A; adding A→C would loop
        val tasks = listOf(
            task("aaaa"),
            task("bbbb", deps = listOf("aaaa")),
            task("cccc", deps = listOf("bbbb")),
        )
        assertTrue(DependencyGraph.wouldCreateCycle(tasks, "aaaa", "cccc"))
    }

    @Test
    fun `diamond is not a cycle`() {
        // B→A, C→A; D depending on both B and C is fine
        val tasks = listOf(
            task("aaaa"),
            task("bbbb", deps = listOf("aaaa")),
            task("cccc", deps = listOf("aaaa")),
            task("dddd"),
        )
        assertFalse(DependencyGraph.wouldCreateCycle(tasks, "dddd", "bbbb"))
        assertFalse(DependencyGraph.wouldCreateCycle(tasks, "dddd", "cccc"))
    }

    @Test
    fun `blocked and ready queries`() {
        val tasks = listOf(
            task("done", completed = true),
            task("open"),
            task("waiting", deps = listOf("open", "done")),
            task("clear", deps = listOf("done")),
        )
        assertTrue(DependencyGraph.isBlocked(tasks, tasks[2]))
        assertFalse(DependencyGraph.isBlocked(tasks, tasks[3]))
        assertEquals(listOf("open"), DependencyGraph.uncompletedBlockers(tasks, tasks[2]).map { it.id })
        assertEquals(setOf("open", "clear"), DependencyGraph.readySet(tasks))
    }

    @Test
    fun `blocking is the reverse edge`() {
        val tasks = listOf(task("aaaa"), task("bbbb", deps = listOf("aaaa")))
        assertEquals(listOf("bbbb"), DependencyGraph.blocking(tasks, tasks[0]).map { it.id })
        assertEquals(emptyList(), DependencyGraph.blocking(tasks, tasks[1]).map { it.id })
    }
}
