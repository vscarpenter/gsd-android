package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskTest {
    private val t0 = Instant.parse("2026-06-15T08:30:00.500Z")

    private fun task(urgent: Boolean = false, important: Boolean = false) =
        Task(title = "t", urgent = urgent, important = important, createdAt = t0, updatedAt = t0)

    @Test
    fun `quadrant is derived from flags, never stored`() {
        assertEquals(Quadrant.DO_FIRST, task(urgent = true, important = true).quadrant)
        assertEquals(Quadrant.ELIMINATE, task().quadrant)
    }

    @Test
    fun `defaults match spec 7-2`() {
        val t = task()
        assertEquals("", t.description)
        assertEquals(false, t.completed)
        assertEquals(Recurrence.NONE, t.recurrence)
        assertEquals(emptyList(), t.tags)
        assertEquals(true, t.notificationEnabled)
        assertEquals(false, t.notificationSent)
    }

    @Test
    fun `timeSpent sums floor-minutes of completed entries only`() {
        val t = task().copy(
            timeEntries = listOf(
                TimeEntry("aaaa1111", t0, t0.plusSeconds(150)), // 2.5 min -> 2
                TimeEntry("bbbb2222", t0, null),                // running -> 0
            ),
        )
        assertEquals(2, t.timeSpent)
    }
}
