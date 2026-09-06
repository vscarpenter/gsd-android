package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskOrderingTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private fun task(title: String, createdOffset: Long, due: Instant? = null, completedAt: Instant? = null) = Task(
        title = title, urgent = false, important = false, completed = completedAt != null,
        completedAt = completedAt, createdAt = t0.plusSeconds(createdOffset), updatedAt = t0, dueDate = due,
    )

    @Test
    fun `active lists sort due ascending, dated before undated, then newest first`() {
        val ordered = TaskOrdering.forList(
            listOf(
                task("undated-old", 0),
                task("due-later", 10, due = t0.plusSeconds(7_200)),
                task("undated-new", 20),
                task("due-soon-old", 30, due = t0.plusSeconds(3_600)),
                task("due-soon-new", 40, due = t0.plusSeconds(3_600)),
            ),
            status = FilterStatus.ACTIVE,
        )
        assertEquals(listOf("due-soon-new", "due-soon-old", "due-later", "undated-new", "undated-old"), ordered.map { it.title })
    }

    @Test
    fun `completed lists sort completedAt descending, nulls last, then newest first`() {
        val ordered = TaskOrdering.forList(
            listOf(
                task("done-early", 0, completedAt = t0.plusSeconds(100)),
                task("done-late", 10, completedAt = t0.plusSeconds(500)),
                task("no-stamp-old", 20).copy(completed = true),
                task("no-stamp-new", 30).copy(completed = true),
            ),
            status = FilterStatus.COMPLETED,
        )
        assertEquals(listOf("done-late", "done-early", "no-stamp-new", "no-stamp-old"), ordered.map { it.title })
    }
}
