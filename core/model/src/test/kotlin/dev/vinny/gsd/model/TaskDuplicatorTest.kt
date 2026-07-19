package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskDuplicatorTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    @Test
    fun `duplicate gets fresh ids, incomplete state, no time entries`() {
        val original = Task(
            title = "t", urgent = true, important = true, completed = true, completedAt = t0,
            createdAt = t0.minusSeconds(3600), updatedAt = t0,
            subtasks = listOf(Subtask("sub1", "one", completed = true), Subtask("sub2", "two")),
            timeEntries = listOf(TimeEntry("abcd1234", t0, t0)),
        )
        val copy = TaskDuplicator.duplicate(original, now = t0)
        assertTrue(copy.id != original.id && copy.id.length == 21)
        assertEquals(false, copy.completed)
        assertNull(copy.completedAt)
        assertEquals(emptyList(), copy.timeEntries)
        assertEquals(listOf("one", "two"), copy.subtasks.map { it.title })
        assertTrue(copy.subtasks.none { it.completed })
        assertTrue(copy.subtasks.map { it.id }.none { it in listOf("sub1", "sub2") })
        assertEquals(t0, copy.createdAt)
    }
}
