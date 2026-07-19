package dev.vinny.gsd.widget

import dev.vinny.gsd.model.Task
import java.time.Instant
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
}
