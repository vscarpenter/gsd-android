package dev.vinny.gsd.widget

import dev.vinny.gsd.model.Task
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetSnapshotTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(
        title: String,
        urgent: Boolean = true,
        important: Boolean = true,
        completed: Boolean = false,
        due: Instant? = null,
    ) = Task(title = title, urgent = urgent, important = important, completed = completed,
        createdAt = now, updatedAt = now, dueDate = due)

    @Test
    fun `snapshot holds q1 active only, due-date first, capped at five`() {
        val tasks = listOf(
            task("no due"),
            task("due soon", due = now.plusSeconds(3_600)),
            task("due later", due = now.plusSeconds(86_400)),
            task("completed q1", completed = true),
            task("not q1", urgent = false, important = false),
            task("extra 1"), task("extra 2"), task("extra 3"),
        )
        val snapshot = WidgetSnapshot.build(tasks, now, ZoneOffset.UTC)
        assertEquals(6, snapshot.totalActive) // six active Q1 tasks
        assertEquals(5, snapshot.items.size)
        assertEquals("due soon", snapshot.items[0].title) // dated before undated
        assertEquals("due later", snapshot.items[1].title)
        assertTrue(snapshot.items.none { it.title == "completed q1" || it.title == "not q1" })
    }

    @Test
    fun `empty q1 yields an all-clear snapshot and json round-trips`() {
        val empty = WidgetSnapshot.build(emptyList(), now, ZoneOffset.UTC)
        assertEquals(0, empty.totalActive)
        val restored = WidgetSnapshot.decode(WidgetSnapshot.encode(empty))
        assertEquals(empty, restored)
    }

    @Test
    fun `a Saved task stays off the widget`() {
        val saved = task("saved q1").copy(savedAt = now.minusSeconds(60))
        val snapshot = WidgetSnapshot.build(listOf(saved, task("active q1")), now, ZoneOffset.UTC)
        assertEquals(1, snapshot.totalActive)
        assertEquals(listOf("active q1"), snapshot.items.map { it.title })
    }

    // --- top three (spec-top-three.md §9) ---

    private val chicago = ZoneId.of("America/Chicago")
    private val wednesday = Instant.parse("2026-11-04T16:00:00Z") // 10:00 on 2026-11-04 in Chicago

    private fun pick(title: String, rank: Int, date: String = "2026-11-04", urgent: Boolean = false) =
        task(title, urgent = urgent, important = false).copy(focusDate = date, focusRank = rank)

    @Test
    fun `picks come first in rank order, from any quadrant`() {
        val tasks = listOf(task("q1 due"), pick("second", 2), pick("first", 1))
        val snapshot = WidgetSnapshot.build(tasks, wednesday, chicago)
        assertEquals("2026-11-04", snapshot.focusDate)
        assertEquals(listOf("first", "second"), snapshot.focusTasks.map { it.title })
        assertEquals(listOf("first", "second"), snapshot.visibleItems("2026-11-04").map { it.title })
    }

    @Test
    fun `at most three picks are shown`() {
        val tasks = listOf(pick("a", 1), pick("b", 2), pick("c", 3), pick("d", 3))
        assertEquals(3, WidgetSnapshot.build(tasks, wednesday, chicago).focusTasks.size)
    }

    @Test
    fun `yesterday's picks fall back to the list at draw time`() {
        val snapshot = WidgetSnapshot.build(listOf(task("q1 due"), pick("picked", 1)), wednesday, chicago)
        assertEquals(listOf("picked"), snapshot.visibleItems("2026-11-04").map { it.title })
        // The widget draws on the next local day with the same stored snapshot
        assertEquals(listOf("q1 due"), snapshot.visibleItems("2026-11-05").map { it.title })
    }

    @Test
    fun `a pick dated another day never reaches the snapshot`() {
        val snapshot = WidgetSnapshot.build(listOf(task("q1 due"), pick("old", 1, date = "2026-11-03")), wednesday, chicago)
        assertEquals(null, snapshot.focusDate)
        assertEquals(emptyList(), snapshot.focusTasks)
        assertEquals(listOf("q1 due"), snapshot.visibleItems("2026-11-04").map { it.title })
    }

    @Test
    fun `a completed or Saved pick isn't shown`() {
        val done = pick("done", 1).copy(completed = true)
        val parked = pick("parked", 2).copy(savedAt = wednesday.minusSeconds(60))
        assertEquals(emptyList(), WidgetSnapshot.build(listOf(done, parked), wednesday, chicago).focusTasks)
    }

    @Test
    fun `a snapshot from before top three still decodes`() {
        val old = """{"items":[{"id":"x","title":"old","dueLabel":""}],"totalActive":1}"""
        val decoded = WidgetSnapshot.decode(old)
        assertEquals(null, decoded.focusDate)
        assertEquals(emptyList(), decoded.focusTasks)
        assertEquals(listOf("old"), decoded.visibleItems("2026-11-04").map { it.title })
    }

    @Test
    fun `picks survive the json round trip`() {
        val snapshot = WidgetSnapshot.build(listOf(pick("a", 1)), wednesday, chicago)
        assertEquals(snapshot, WidgetSnapshot.decode(WidgetSnapshot.encode(snapshot)))
    }
}
