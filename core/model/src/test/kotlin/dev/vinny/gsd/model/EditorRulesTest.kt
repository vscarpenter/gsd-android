package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorRulesTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(due: Instant? = t0, notifyBefore: Int? = 15) =
        Task(
            title = "t", urgent = false, important = false, createdAt = t0, updatedAt = t0,
            dueDate = due, notifyBefore = notifyBefore,
            notificationSent = true, lastNotificationAt = t0, snoozedUntil = t0.plusSeconds(3600),
        )

    @Test
    fun `changing dueDate resets reminder state`() {
        val edited = EditorRules.applyReminderReset(task(), task(due = t0.plusSeconds(86_400)))
        assertEquals(false, edited.notificationSent)
        assertNull(edited.lastNotificationAt)
        assertNull(edited.snoozedUntil)
    }

    @Test
    fun `changing notifyBefore resets reminder state`() {
        val edited = EditorRules.applyReminderReset(task(), task(notifyBefore = 30))
        assertEquals(false, edited.notificationSent)
    }

    @Test
    fun `unrelated edits keep reminder state`() {
        val edited = EditorRules.applyReminderReset(task(), task().copy(title = "renamed"))
        assertEquals(true, edited.notificationSent)
        assertEquals(t0, edited.lastNotificationAt)
    }
}
