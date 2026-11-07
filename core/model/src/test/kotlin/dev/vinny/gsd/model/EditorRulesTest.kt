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

    /** This device's default reminder, which a legacy null offset fires (D-10). */
    private fun merge(current: Task, original: Task, edited: Task, defaultReminder: Int = 15): Task =
        EditorRules.merge(current, original, edited, defaultReminder)

    @Test
    fun `resolving a null offset doesn't re-arm a sent reminder`() {
        val legacy = task(notifyBefore = null) // fires the default, 15, and already posted
        val merged = merge(legacy, original = legacy, edited = legacy.copy(notifyBefore = 15), defaultReminder = 15)
        assertEquals(true, merged.notificationSent)
        assertEquals(t0, merged.lastNotificationAt)
    }

    @Test
    fun `a real change to the offset still re-arms`() {
        val legacy = task(notifyBefore = null)
        val merged = merge(legacy, original = legacy, edited = legacy.copy(notifyBefore = 30), defaultReminder = 15)
        assertEquals(false, merged.notificationSent)
        assertNull(merged.lastNotificationAt)
    }

    @Test
    fun `changing dueDate resets reminder state`() {
        val edited = merge(task(), original = task(), edited = task(due = t0.plusSeconds(86_400)))
        assertEquals(false, edited.notificationSent)
        assertNull(edited.lastNotificationAt)
        assertNull(edited.snoozedUntil)
    }

    @Test
    fun `changing notifyBefore resets reminder state`() {
        val edited = merge(task(), original = task(), edited = task(notifyBefore = 30))
        assertEquals(false, edited.notificationSent)
    }

    @Test
    fun `unrelated edits keep reminder state`() {
        val edited = merge(task(), original = task(), edited = task().copy(title = "renamed"))
        assertEquals(true, edited.notificationSent)
        assertEquals(t0, edited.lastNotificationAt)
    }

    // --- merge (review finding 44): what Save writes when the stored row moved while the editor was open ---

    private val opened = Task(title = "opened", urgent = true, important = true, createdAt = t0, updatedAt = t0)

    @Test
    fun `a completion made while the editor was open survives the save`() {
        val current = opened.copy(completed = true, completedAt = t0.plusSeconds(60), updatedAt = t0.plusSeconds(60))
        val merged = merge(current, original = opened, edited = opened.copy(title = "retitled"))
        assertEquals("retitled", merged.title)
        assertEquals(true, merged.completed)
        assertEquals(t0.plusSeconds(60), merged.completedAt)
    }

    @Test
    fun `a field the user changed wins over a newer stored value`() {
        val current = opened.copy(title = "pulled from another device", description = "pulled notes")
        val merged = merge(current, original = opened, edited = opened.copy(title = "typed here"))
        assertEquals("typed here", merged.title)
        assertEquals("pulled notes", merged.description) // untouched here, so the newer stored value stays
    }

    @Test
    fun `a snooze set elsewhere survives a save that never touched the snooze`() {
        val snoozed = opened.copy(snoozedUntil = t0.plusSeconds(3600)) // the reminder's Snooze button
        val merged = merge(snoozed, original = opened, edited = opened.copy(title = "retitled"))
        assertEquals(t0.plusSeconds(3600), merged.snoozedUntil)
    }

    @Test
    fun `a snooze the user cleared in the editor stays cleared`() {
        val original = opened.copy(snoozedUntil = t0.plusSeconds(3600))
        val merged = merge(current = original, original = original, edited = original.copy(snoozedUntil = null))
        assertNull(merged.snoozedUntil)
    }

    @Test
    fun `moving the due date in the editor still resets the stored reminder state`() {
        val current = opened.copy(dueDate = t0, notificationSent = true, lastNotificationAt = t0)
        val original = opened.copy(dueDate = t0)
        val merged = merge(current, original, edited = original.copy(dueDate = t0.plusSeconds(86_400)))
        assertEquals(t0.plusSeconds(86_400), merged.dueDate)
        assertEquals(false, merged.notificationSent)
        assertNull(merged.lastNotificationAt)
    }

    @Test
    fun `time tracked while the editor was open is kept`() {
        val entry = TimeEntry(id = "entry001", startedAt = t0, endedAt = t0.plusSeconds(600))
        val current = opened.copy(timeEntries = listOf(entry))
        val merged = merge(current, original = opened, edited = opened.copy(estimatedMinutes = 30))
        assertEquals(listOf(entry), merged.timeEntries)
        assertEquals(30, merged.estimatedMinutes)
    }

    /**
     * Every field the editor owns: a value the user typed, and how to read it back. Found by the
     * reviewer of #22. Four of the twelve were pinned, so dropping any of the other eight from merge
     * stayed green, and that field would have been lost on every save.
     */
    private val ownedFields: List<Triple<String, (Task) -> Task, (Task) -> Any?>> = listOf(
        Triple("title", { it.copy(title = "typed") }, { it.title }),
        Triple("description", { it.copy(description = "typed notes") }, { it.description }),
        Triple("urgent", { it.copy(urgent = false) }, { it.urgent }),
        Triple("important", { it.copy(important = false) }, { it.important }),
        Triple("tags", { it.copy(tags = listOf("typed")) }, { it.tags }),
        Triple("dueDate", { it.copy(dueDate = t0.plusSeconds(86_400)) }, { it.dueDate }),
        Triple("recurrence", { it.copy(recurrence = Recurrence.WEEKLY) }, { it.recurrence }),
        Triple("subtasks", { it.copy(subtasks = listOf(Subtask(id = "sub00001", title = "typed"))) }, { it.subtasks }),
        Triple("dependencies", { it.copy(dependencies = listOf("dep-task-0000000000001")) }, { it.dependencies }),
        Triple("notifyBefore", { it.copy(notifyBefore = 45) }, { it.notifyBefore }),
        Triple("notificationEnabled", { it.copy(notificationEnabled = false) }, { it.notificationEnabled }),
        Triple("estimatedMinutes", { it.copy(estimatedMinutes = 90) }, { it.estimatedMinutes }),
        Triple("snoozedUntil", { it.copy(snoozedUntil = t0.plusSeconds(7_200)) }, { it.snoozedUntil }),
    )

    @Test
    fun `every field the editor owns keeps the value the user typed`() {
        assertEquals(13, ownedFields.size)
        ownedFields.forEach { (field, type, read) ->
            val edited = type(opened)
            val merged = merge(current = opened, original = opened, edited = edited)
            assertEquals(read(edited), read(merged), "merge dropped the user's $field")
        }
    }

    @Test
    fun `every field the editor owns keeps a newer stored value the user never touched`() {
        ownedFields.forEach { (field, change, read) ->
            val current = change(opened) // changed in the store while the sheet was open
            val merged = merge(current, original = opened, edited = opened)
            assertEquals(read(current), read(merged), "merge reverted a stored $field the user never touched")
        }
    }
}
