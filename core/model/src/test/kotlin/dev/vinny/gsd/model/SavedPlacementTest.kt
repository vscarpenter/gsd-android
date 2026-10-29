package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Saved placement (web ADR 0018, iOS ADR 0001; spec 2026-10-29 §3). */
class SavedPlacementTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-10-29T15:00:00.000Z")
    private val savedAt = Instant.parse("2026-10-26T12:00:00.000Z")

    private fun task(
        savedAt: Instant? = null,
        completed: Boolean = false,
        urgent: Boolean = false,
        important: Boolean = true,
        dueDate: Instant? = now.plusSeconds(86_400),
        timeEntries: List<TimeEntry> = emptyList(),
        dependencies: List<String> = emptyList(),
        id: String = NanoId.generate(),
    ) = Task(
        id = id, title = "t", urgent = urgent, important = important, completed = completed,
        createdAt = now.minusSeconds(3_600), updatedAt = now.minusSeconds(3_600), dueDate = dueDate,
        timeEntries = timeEntries, dependencies = dependencies, savedAt = savedAt,
        notificationSent = true, lastNotificationAt = now.minusSeconds(60), snoozedUntil = now.plusSeconds(600),
    )

    @Test
    fun `savedAt round-trips through TaskJson and is omitted when null`() {
        val saved = task(savedAt = savedAt)
        val obj = Json.parseToJsonElement(TaskJson.encode(saved)).jsonObject
        assertEquals("2026-10-26T12:00:00.000Z", obj.getValue("savedAt").jsonPrimitive.content)
        assertEquals(saved, TaskJson.decodeOrNull(obj))
        assertNull(Json.parseToJsonElement(TaskJson.encode(task())).jsonObject["savedAt"])
    }

    @Test
    fun `save stamps savedAt and keeps everything else`() {
        val before = task()
        val after = SavedPlacement.save(before, now)
        assertEquals(now, after.savedAt)
        assertTrue(after.isSaved)
        assertEquals(before.copy(savedAt = now), after)
    }

    @Test
    fun `saving an already saved task keeps the original stamp`() {
        assertEquals(savedAt, SavedPlacement.save(task(savedAt = savedAt), now).savedAt)
    }

    @Test
    fun `a completed task can't be saved`() {
        assertFalse(SavedPlacement.canSave(task(completed = true)))
        assertFailsWith<SavedPlacement.CannotSave> { SavedPlacement.save(task(completed = true), now) }
    }

    @Test
    fun `a task with a running timer can't be saved`() {
        val timing = task(timeEntries = listOf(TimeEntry("e1", now.minusSeconds(300))))
        assertFalse(SavedPlacement.canSave(timing))
        assertFailsWith<SavedPlacement.CannotSave> { SavedPlacement.save(timing, now) }
    }

    @Test
    fun `return clears placement, applies the reviewed quadrant and date, and resets reminder state`() {
        val newDue = Instant.parse("2026-11-02T06:00:00.000Z")
        val back = SavedPlacement.returnToMatrix(task(savedAt = savedAt), urgent = true, important = false, dueDate = newDue)
        assertNull(back.savedAt)
        assertEquals(Quadrant.from(urgent = true, important = false), back.quadrant)
        assertEquals(newDue, back.dueDate)
        assertFalse(back.notificationSent)
        assertNull(back.lastNotificationAt)
        assertNull(back.snoozedUntil)
        assertFalse(back.completed)
    }

    @Test
    fun `an incoming return resets this device's reminder state`() {
        val local = task(savedAt = savedAt)
        val incoming = local.copy(savedAt = null)
        val merged = SavedPlacement.afterRemoteChange(local, incoming)
        assertFalse(merged.notificationSent)
        assertNull(merged.lastNotificationAt)
        assertNull(merged.snoozedUntil)
    }

    @Test
    fun `an incoming change that isn't a return leaves reminder state alone`() {
        val local = task()
        val incoming = local.copy(title = "renamed")
        assertEquals(incoming, SavedPlacement.afterRemoteChange(local, incoming))
        assertEquals(incoming, SavedPlacement.afterRemoteChange(null, incoming))
    }

    @Test
    fun `smart views and filters leave Saved out`() {
        val saved = task(savedAt = savedAt)
        assertFalse(FilterCriteria().matches(saved, listOf(saved), now, zone))
        assertFalse(FilterCriteria(searchQuery = "t").matches(saved, listOf(saved), now, zone))
    }

    @Test
    fun `a Saved prerequisite still blocks its dependent`() {
        val prerequisite = task(savedAt = savedAt, id = "pre")
        val dependent = task(dependencies = listOf("pre"), id = "dep")
        val ready = FilterCriteria(status = FilterStatus.ACTIVE, readyToWork = true)
        assertFalse(ready.matches(dependent, listOf(prerequisite, dependent), now, zone))
    }

    @Test
    fun `analytics leave Saved out`() {
        val stats = Analytics.compute(listOf(task(), task(savedAt = savedAt)), 7, now, zone)
        assertEquals(1, stats.active)
        assertEquals(1, stats.upcoming.size)
        assertEquals(1, stats.quadrantStats.sumOf { it.total })
    }

    @Test
    fun `a Saved task never gets a reminder`() {
        val settings = ReminderSettings()
        assertNull(ReminderPlanner.fireTime(task(savedAt = savedAt).copy(notificationSent = false, snoozedUntil = null), settings, now, zone))
    }
}
