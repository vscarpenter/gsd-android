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

    private val allDayDue = Instant.parse("2026-07-21T00:00:00.000Z") // local midnight in UTC: all day
    private val hour = ReminderPlanner.ALL_DAY_REMINDER_HOUR

    @Test
    fun `an all-day task fires at the all-day hour on the due day, whatever the offset under a day`() {
        val expected = Instant.parse("2026-07-21T00:00:00.000Z").plusSeconds(hour * 3600L)
        listOf(0, 15, 120, 1439).forEach { minutes ->
            assertEquals(expected, ReminderPlanner.fireTime(task(allDayDue, notifyBefore = minutes), settings, now, zone), "$minutes minutes")
        }
        assertEquals(expected, ReminderPlanner.fireTime(task(allDayDue), settings, now, zone), "the default offset")
    }

    @Test
    fun `each whole day of offset moves an all-day reminder back one day`() {
        val oneDay = Instant.parse("2026-07-20T00:00:00.000Z").plusSeconds(hour * 3600L)
        assertEquals(oneDay, ReminderPlanner.fireTime(task(allDayDue, notifyBefore = 1440), settings, now, zone))
        assertEquals(oneDay.minusSeconds(86_400),
            ReminderPlanner.fireTime(task(allDayDue, notifyBefore = 2880), settings, now.minusSeconds(86_400 * 2), zone))
    }

    @Test
    fun `a timed task still fires its offset before the due time`() {
        assertEquals(due.minusSeconds(30 * 60), ReminderPlanner.fireTime(task(notifyBefore = 30), settings, now, zone))
    }

    @Test
    fun `fire time is due minus per-task offset, falling back to the default`() {
        assertEquals(due.minusSeconds(15 * 60),
            ReminderPlanner.fireTime(task(), settings, now, zone))
        assertEquals(due.minusSeconds(120 * 60),
            ReminderPlanner.fireTime(task(notifyBefore = 120), settings, now, zone))
        assertEquals(due, ReminderPlanner.fireTime(task(notifyBefore = 0), settings, now, zone))
    }

    @Test
    fun `a future snooze that ends after the reminder time replaces it`() {
        val snooze = due.plusSeconds(3600) // the reminder itself is due minus 15 minutes
        assertEquals(snooze, ReminderPlanner.fireTime(task(snoozedUntil = snooze), settings, now, zone))
    }

    /** Owner decision 2026-11-03, web #669: a short snooze on a task due tomorrow used to lose tomorrow's reminder. */
    @Test
    fun `a future snooze that ends before the reminder time leaves the reminder on time`() {
        val snooze = now.plusSeconds(3600)
        assertEquals(due.minusSeconds(15 * 60), ReminderPlanner.fireTime(task(snoozedUntil = snooze), settings, now, zone))
    }

    /**
     * A posted reminder whose time is still ahead, as after a zone change moves an all-day reminder later. The
     * snooze reopens it, and v2 then keeps the later reminder time. The web clears the sent flag on a snooze and
     * gets the same answer. Found by `NotificationsTest.aReminderSnoozedFromItsNotificationIsPlannedAgain`.
     */
    @Test
    fun `a snooze on a posted reminder that ends before the reminder time leaves the reminder on time`() {
        val posted = task(notificationSent = true, snoozedUntil = now.plusSeconds(3600))
        assertEquals(due.minusSeconds(15 * 60), ReminderPlanner.fireTime(posted, settings, now, zone))
    }

    /** Review finding 5: "Snooze 1 hour" on a reminder that had already posted never fired again. */
    @Test
    fun `a snooze asks for another reminder after the first one posted`() {
        val posted = task(dueDate = now.plusSeconds(600), notificationSent = true) // its reminder time passed 5 minutes ago
        val snooze = now.plusSeconds(3600)
        assertEquals(snooze, ReminderPlanner.fireTime(posted.copy(snoozedUntil = snooze), settings, now, zone))
    }

    /** Once the snoozed reminder posts, its snooze is in the past. The sent flag must hold, or it would loop. */
    @Test
    fun `a posted reminder with a spent snooze stays quiet`() {
        val spent = now.minusSeconds(60)
        assertNull(ReminderPlanner.fireTime(task(notificationSent = true, snoozedUntil = spent), settings, now, zone))
    }

    @Test
    fun `a snooze never revives a completed or silenced task`() {
        val snooze = now.plusSeconds(3600)
        val posted = task(notificationSent = true, snoozedUntil = snooze)
        assertNull(ReminderPlanner.fireTime(posted.copy(completed = true), settings, now, zone))
        assertNull(ReminderPlanner.fireTime(posted.copy(notificationEnabled = false), settings, now, zone))
        assertNull(ReminderPlanner.fireTime(posted, settings.copy(enabled = false), now, zone))
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

    private fun dueIn(hours: Long, id: String) = task(dueDate = now.plusSeconds(hours * 3600)).copy(id = id)

    /** Android refuses more than 500 concurrent alarms per app, so a large import must not ask for one per task. */
    @Test
    fun `soonest keeps the nearest fire times up to the limit`() {
        val tasks = listOf(dueIn(5, "e"), dueIn(1, "a"), dueIn(4, "d"), dueIn(2, "b"), dueIn(3, "c"))

        val planned = ReminderPlanner.soonest(tasks, settings, now, zone, limit = 3)

        assertEquals(setOf("a", "b", "c"), planned.keys)
    }

    @Test
    fun `a task with no fire time never takes a slot`() {
        val done = listOf(dueIn(1, "x").copy(completed = true), dueIn(1, "y").copy(completed = true))
        val open = listOf(dueIn(8, "a"), dueIn(9, "b"))

        val planned = ReminderPlanner.soonest(done + open, settings, now, zone, limit = 2)

        assertEquals(setOf("a", "b"), planned.keys)
    }

    /** Equal fire times break by id, so every resync picks the same tasks and no alarm flaps. */
    @Test
    fun `equal fire times break by id`() {
        val tasks = listOf(dueIn(2, "c"), dueIn(2, "a"), dueIn(2, "b"))

        val planned = ReminderPlanner.soonest(tasks, settings, now, zone, limit = 2)

        assertEquals(setOf("a", "b"), planned.keys)
    }
}

/** Review finding 7: reminders default to on, so the ask on the Settings switch never ran. */
class ReminderPermissionTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private val fresh = ReminderSettings(enabled = true, permissionAsked = false)
    private val noDate = Task(title = "t", urgent = false, important = false, createdAt = now, updatedAt = now)
    private val dated = noDate.copy(dueDate = now.plusSeconds(86_400))

    @Test
    fun `asks when an edit first gives a task a due date`() =
        assertEquals(true, ReminderPermission.shouldAsk(fresh, granted = false, before = noDate, after = dated))

    @Test
    fun `asks when an edit changes the reminder on a dated task`() =
        assertEquals(true, ReminderPermission.shouldAsk(fresh, false, dated, dated.copy(notifyBefore = 60)))

    /** B-36: picking an offset from None gives the task a reminder, even when the number stays the same. */
    @Test
    fun `asks when an edit turns a task's reminder back on`() {
        val off = dated.copy(notifyBefore = 15, notificationEnabled = false)
        assertEquals(true, ReminderPermission.shouldAsk(fresh, false, off, off.copy(notificationEnabled = true)))
    }

    /** D-10, section 4: a legacy null already fires the default, so picking that number changes nothing. */
    @Test
    fun `never asks when an edit resolves a null offset to the default`() =
        assertEquals(false, ReminderPermission.shouldAsk(fresh, false, dated, dated.copy(notifyBefore = fresh.defaultReminderMinutes)))

    @Test
    fun `never asks twice`() =
        assertEquals(false, ReminderPermission.shouldAsk(fresh.copy(permissionAsked = true), false, noDate, dated))

    @Test
    fun `never asks once granted`() =
        assertEquals(false, ReminderPermission.shouldAsk(fresh, granted = true, before = noDate, after = dated))

    @Test
    fun `never asks while reminders are off`() =
        assertEquals(false, ReminderPermission.shouldAsk(fresh.copy(enabled = false), false, noDate, dated))

    @Test
    fun `never asks for a task whose reminders are off`() =
        assertEquals(false, ReminderPermission.shouldAsk(fresh, false, noDate, dated.copy(notificationEnabled = false)))

    @Test
    fun `never asks for an edit that leaves the due date and reminder alone`() =
        assertEquals(false, ReminderPermission.shouldAsk(fresh, false, dated, dated.copy(title = "renamed")))

    @Test
    fun `never asks when an edit removes the due date`() =
        assertEquals(false, ReminderPermission.shouldAsk(fresh, false, dated, noDate))

    /** Review, "Also worth a look": stored quiet hours went through LocalTime.parse unchecked. */
    @Test
    fun `quiet hours keep both times only when both parse`() {
        assertEquals(LocalTime.of(22, 0) to LocalTime.of(7, 0), QuietHours.parse("22:00", "07:00"))
        assertEquals(null to null, QuietHours.parse("25:00", "07:00"))
        assertEquals(null to null, QuietHours.parse("22:00", "7am"))
        assertEquals(null to null, QuietHours.parse("22:00", null))
        assertEquals(null to null, QuietHours.parse(null, null))
    }
}
