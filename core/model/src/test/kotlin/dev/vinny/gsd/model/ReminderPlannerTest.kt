package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderPlannerTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private val due = Instant.parse("2026-07-20T15:00:00.000Z")

    private val settings = ReminderSettings(
        enabled = true, defaultReminderMinutes = 15, soundEnabled = true,
        quietHoursStart = null, quietHoursEnd = null,
    )

    private fun task(
        dueDate: Instant? = due,
        notifyBefore: Int? = null,
        snoozedUntil: Instant? = null,
        completed: Boolean = false,
        notificationEnabled: Boolean = true,
        notificationSent: Boolean = false,
    ) = Task(
        title = "t", urgent = false, important = false, completed = completed,
        createdAt = now, updatedAt = now, dueDate = dueDate, notifyBefore = notifyBefore,
        notificationEnabled = notificationEnabled, notificationSent = notificationSent,
        snoozedUntil = snoozedUntil,
    )

    @Test
    fun `fire time is due minus per-task offset, falling back to the default`() {
        assertEquals(due.minusSeconds(15 * 60),
            ReminderPlanner.fireTime(task(), settings, now, zone))
        assertEquals(due.minusSeconds(120 * 60),
            ReminderPlanner.fireTime(task(notifyBefore = 120), settings, now, zone))
        assertEquals(due, ReminderPlanner.fireTime(task(notifyBefore = 0), settings, now, zone))
    }

    @Test
    fun `future snooze replaces the computed time entirely`() {
        val snooze = now.plusSeconds(3600)
        assertEquals(snooze, ReminderPlanner.fireTime(task(snoozedUntil = snooze), settings, now, zone))
    }

    @Test
    fun `skip rules yield null`() {
        assertNull(ReminderPlanner.fireTime(task(), settings.copy(enabled = false), now, zone))
        assertNull(ReminderPlanner.fireTime(task(notificationEnabled = false), settings, now, zone))
        assertNull(ReminderPlanner.fireTime(task(completed = true), settings, now, zone))
        assertNull(ReminderPlanner.fireTime(task(dueDate = null), settings, now, zone))
        assertNull(ReminderPlanner.fireTime(task(notificationSent = true), settings, now, zone))
    }

    @Test
    fun `past fire time yields null`() {
        val pastDue = now.minusSeconds(3600)
        assertNull(ReminderPlanner.fireTime(task(dueDate = pastDue), settings, now, zone))
    }

    @Test
    fun `quiet hours defer to window end, including cross-midnight`() {
        val quiet = settings.copy(
            quietHoursStart = LocalTime.of(22, 0),
            quietHoursEnd = LocalTime.of(7, 0),
        )
        // fire at 23:00 → deferred to 07:00 next day
        val lateDue = Instant.parse("2026-07-20T23:15:00.000Z")
        assertEquals(
            Instant.parse("2026-07-21T07:00:00.000Z"),
            ReminderPlanner.fireTime(task(dueDate = lateDue), quiet, now, zone),
        )
        // fire at 03:00 → deferred to 07:00 same day
        val earlyDue = Instant.parse("2026-07-21T03:15:00.000Z")
        assertEquals(
            Instant.parse("2026-07-21T07:00:00.000Z"),
            ReminderPlanner.fireTime(task(dueDate = earlyDue), quiet, now, zone),
        )
        // fire at 12:00 → untouched
        assertEquals(
            due.minusSeconds(900),
            ReminderPlanner.fireTime(task(), quiet, now, zone),
        )
    }

    @Test
    fun `same-day quiet window defers only inside it`() {
        val quiet = settings.copy(
            quietHoursStart = LocalTime.of(13, 0),
            quietHoursEnd = LocalTime.of(15, 0),
        )
        val duringDue = Instant.parse("2026-07-20T14:15:00.000Z") // fires 14:00 → inside
        assertEquals(
            Instant.parse("2026-07-20T15:00:00.000Z"),
            ReminderPlanner.fireTime(task(dueDate = duringDue), quiet, now, zone),
        )
    }
}
