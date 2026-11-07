package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

/** D-10: `notify_before` is always a number (web `tasks/spec-notify-before.md`, sections 2 and 3). */
class ReminderOffsetTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private val due = Instant.parse("2026-07-20T15:00:00.000Z")

    private fun task(notifyBefore: Int?) = Task(
        title = "t", urgent = false, important = false, createdAt = now, updatedAt = now,
        dueDate = due, notifyBefore = notifyBefore,
    )

    private fun settings(defaultReminder: Int) = ReminderSettings(defaultReminderMinutes = defaultReminder)

    private fun minutesBeforeDue(minutes: Long): Instant = due.minusSeconds(minutes * 60)

    @Test
    fun `a zero offset stays zero and fires at the due time`() {
        assertEquals(0, ReminderOffset.resolve(task(0), defaultReminder = 15).notifyBefore)
        assertEquals(due, ReminderPlanner.fireTime(task(0), settings(15), now, zone))
    }

    @Test
    fun `changing the default doesn't move an existing number`() {
        assertEquals(30, ReminderOffset.resolve(task(30), defaultReminder = 60).notifyBefore)
        assertEquals(minutesBeforeDue(30), ReminderPlanner.fireTime(task(30), settings(60), now, zone))
    }

    /** Never went red: the planner already read the effective value. A mutation of its fallback proves it. */
    @Test
    fun `a legacy null fires the default until it's written`() {
        assertEquals(minutesBeforeDue(15), ReminderPlanner.fireTime(task(null), settings(15), now, zone))
        assertEquals(minutesBeforeDue(60), ReminderPlanner.fireTime(task(null), settings(60), now, zone))
        assertEquals(60, ReminderOffset.resolve(task(null), defaultReminder = 60).notifyBefore)
    }

    @Test
    fun `the effective offset is the stored number, else this device's default`() {
        assertEquals(30, ReminderOffset.effective(task(30), defaultReminder = 15))
        assertEquals(0, ReminderOffset.effective(task(0), defaultReminder = 15))
        assertEquals(15, ReminderOffset.effective(task(null), defaultReminder = 15))
    }
}
