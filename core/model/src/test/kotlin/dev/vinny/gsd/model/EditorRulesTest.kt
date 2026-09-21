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
        val edited = EditorRules.merge(task(), original = task(), edited = task(due = t0.plusSeconds(86_400)))
        assertEquals(false, edited.notificationSent)
        assertNull(edited.lastNotificationAt)
        assertNull(edited.snoozedUntil)
    }

    @Test
    fun `changing notifyBefore resets reminder state`() {
        val edited = EditorRules.merge(task(), original = task(), edited = task(notifyBefore = 30))
        assertEquals(false, edited.notificationSent)
    }

    @Test
    fun `unrelated edits keep reminder state`() {
        val edited = EditorRules.merge(task(), original = task(), edited = task().copy(title = "renamed"))
        assertEquals(true, edited.notificationSent)
        assertEquals(t0, edited.lastNotificationAt)
    }

    // --- merge (review finding 44): what Save writes when the stored row moved while the editor was open ---

    private val opened = Task(title = "opened", urgent = true, important = true, createdAt = t0, updatedAt = t0)

    @Test
    fun `a completion made while the editor was open survives the save`() {
        val current = opened.copy(completed = true, completedAt = t0.plusSeconds(60), updatedAt = t0.plusSeconds(60))
        val merged = EditorRules.merge(current, original = opened, edited = opened.copy(title = "retitled"))
        assertEquals("retitled", merged.title)
        assertEquals(true, merged.completed)
        assertEquals(t0.plusSeconds(60), merged.completedAt)
    }

    @Test
    fun `a field the user changed wins over a newer stored value`() {
        val current = opened.copy(title = "pulled from another device", description = "pulled notes")
        val merged = EditorRules.merge(current, original = opened, edited = opened.copy(title = "typed here"))
        assertEquals("typed here", merged.title)
        assertEquals("pulled notes", merged.description) // untouched here, so the newer stored value stays
    }

    @Test
    fun `a snooze set elsewhere survives a save that never touched the snooze`() {
        val snoozed = opened.copy(snoozedUntil = t0.plusSeconds(3600)) // the reminder's Snooze button
        val merged = EditorRules.merge(snoozed, original = opened, edited = opened.copy(title = "retitled"))
        assertEquals(t0.plusSeconds(3600), merged.snoozedUntil)
    }

    @Test
    fun `a snooze the user cleared in the editor stays cleared`() {
        val original = opened.copy(snoozedUntil = t0.plusSeconds(3600))
        val merged = EditorRules.merge(current = original, original = original, edited = original.copy(snoozedUntil = null))
        assertNull(merged.snoozedUntil)
    }

    @Test
    fun `moving the due date in the editor still resets the stored reminder state`() {
        val current = opened.copy(dueDate = t0, notificationSent = true, lastNotificationAt = t0)
        val original = opened.copy(dueDate = t0)
        val merged = EditorRules.merge(current, original, edited = original.copy(dueDate = t0.plusSeconds(86_400)))
        assertEquals(t0.plusSeconds(86_400), merged.dueDate)
        assertEquals(false, merged.notificationSent)
        assertNull(merged.lastNotificationAt)
    }

    @Test
    fun `time tracked while the editor was open is kept`() {
        val entry = TimeEntry(id = "entry001", startedAt = t0, endedAt = t0.plusSeconds(600))
        val current = opened.copy(timeEntries = listOf(entry))
        val merged = EditorRules.merge(current, original = opened, edited = opened.copy(estimatedMinutes = 30))
        assertEquals(listOf(entry), merged.timeEntries)
        assertEquals(30, merged.estimatedMinutes)
    }
}
