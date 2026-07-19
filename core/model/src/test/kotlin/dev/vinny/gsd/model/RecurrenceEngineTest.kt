package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecurrenceEngineTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-07-19T15:00:00.000Z")

    private fun recurring(due: Instant?, recurrence: Recurrence, parent: String? = null) =
        Task(
            title = "r", urgent = true, important = false, completed = true,
            completedAt = now, createdAt = now.minusSeconds(86_400), updatedAt = now,
            dueDate = due, recurrence = recurrence, parentTaskId = parent,
            subtasks = listOf(Subtask("sub1", "step", completed = true)),
            timeEntries = listOf(TimeEntry("abcd1234", now.minusSeconds(600), now)),
            notificationSent = true, lastNotificationAt = now, snoozedUntil = now.plusSeconds(3600),
        )

    @Test
    fun `none spawns nothing`() =
        assertNull(RecurrenceEngine.spawnNext(recurring(now, Recurrence.NONE), now))

    @Test
    fun `daily advances one day from the prior due date`() {
        val due = Instant.parse("2026-07-10T09:00:00.000Z")
        val next = RecurrenceEngine.spawnNext(recurring(due, Recurrence.DAILY), now)!!
        assertEquals(Instant.parse("2026-07-11T09:00:00.000Z"), next.dueDate)
    }

    @Test
    fun `weekly advances seven days`() {
        val due = Instant.parse("2026-07-10T09:00:00.000Z")
        val next = RecurrenceEngine.spawnNext(recurring(due, Recurrence.WEEKLY), now)!!
        assertEquals(Instant.parse("2026-07-17T09:00:00.000Z"), next.dueDate)
    }

    @Test
    fun `monthly clamps to month end`() {
        val jan31 = Instant.parse("2026-01-31T09:00:00.000Z")
        val next = RecurrenceEngine.spawnNext(recurring(jan31, Recurrence.MONTHLY), now)!!
        assertEquals(Instant.parse("2026-02-28T09:00:00.000Z"), next.dueDate) // 2026 not a leap year

        val jan31Leap = Instant.parse("2028-01-31T09:00:00.000Z")
        val nextLeap = RecurrenceEngine.spawnNext(recurring(jan31Leap, Recurrence.MONTHLY), now)!!
        assertEquals(Instant.parse("2028-02-29T09:00:00.000Z"), nextLeap.dueDate) // 2028 is
    }

    @Test
    fun `spawn resets state and keeps single-level lineage`() {
        val original = recurring(now, Recurrence.DAILY)
        val next = RecurrenceEngine.spawnNext(original, now)!!
        assertTrue(next.id != original.id && next.id.length == 21)
        assertEquals(false, next.completed)
        assertNull(next.completedAt)
        assertEquals(now, next.createdAt)
        assertEquals(now, next.updatedAt)
        assertEquals(original.id, next.parentTaskId)
        assertTrue(next.subtasks.all { !it.completed })
        assertEquals(listOf("sub1"), next.subtasks.map { it.id })
        assertEquals(emptyList(), next.timeEntries)
        assertEquals(false, next.notificationSent)
        assertNull(next.lastNotificationAt)
        assertNull(next.snoozedUntil)

        // an instance's spawn points at the ORIGINAL, not the instance
        val grandchild = RecurrenceEngine.spawnNext(next.copy(completed = true, completedAt = now), now)!!
        assertEquals(original.id, grandchild.parentTaskId)
    }

    @Test
    fun `no due date spawns with no due date`() =
        assertNull(RecurrenceEngine.spawnNext(recurring(null, Recurrence.DAILY), now)!!.dueDate)
}
