package dev.vinny.gsd.matrix

import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** Saved tasks live outside the matrix (web ADR 0018; spec 2026-10-29 §3.4). */
class BuildSectionsSavedTest {
    private val now = Instant.parse("2026-10-29T15:00:00.000Z")

    private fun task(title: String, savedAt: Instant? = null) = Task(
        title = title, urgent = true, important = true, createdAt = now, updatedAt = now, savedAt = savedAt,
    )

    @Test
    fun `a Saved task is neither listed nor counted`() {
        val doFirst = buildSections(listOf(task("active"), task("saved", savedAt = now)))
            .single { it.quadrant == Quadrant.DO_FIRST }
        assertEquals(listOf("active"), doFirst.tasks.map { it.title })
        assertEquals(1, doFirst.activeCount)
    }
}
