package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareTextTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    @Test
    fun `full render has title, quadrant, date, tags, description`() {
        val task = Task(
            title = "Ship the deck", urgent = true, important = true,
            description = "Final polish pass", tags = listOf("work", "q3"),
            dueDate = Instant.parse("2026-07-24T09:00:00.000Z"),
            createdAt = t0, updatedAt = t0,
        )
        assertEquals(
            """
            Ship the deck
            Do First
            Jul 24, 2026
            #work #q3

            Final polish pass
            """.trimIndent(),
            ShareText.render(task, ZoneOffset.UTC),
        )
    }

    @Test
    fun `minimal render is title and quadrant only`() {
        val task = Task(title = "Call mom", urgent = true, important = false, createdAt = t0, updatedAt = t0)
        assertEquals("Call mom\nDelegate", ShareText.render(task, ZoneOffset.UTC))
    }
}
