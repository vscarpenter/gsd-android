package dev.vinny.gsd.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskValidatorTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(title: String = "ok") =
        Task(title = title, urgent = false, important = false, createdAt = now, updatedAt = now)

    @Test
    fun `tags are trimmed lowercased deduped and empties dropped`() =
        assertEquals(listOf("auth", "work"), TaskValidator.normalizeTags(listOf(" Auth", "AUTH", "work", "  ")))

    @Test
    fun `title bounds 1-80`() {
        assertFailsWith<TaskValidationError.TitleLength> { TaskValidator.validate(task(""), now) }
        assertFailsWith<TaskValidationError.TitleLength> { TaskValidator.validate(task("x".repeat(81)), now) }
        TaskValidator.validate(task("x".repeat(80)), now) // no throw
    }

    @Test
    fun `description cap 600`() {
        assertFailsWith<TaskValidationError.DescriptionLength> {
            TaskValidator.validate(task().copy(description = "d".repeat(601)), now)
        }
    }

    @Test
    fun `tag count cap 20 and length 1-30`() {
        assertFailsWith<TaskValidationError.TooManyTags> {
            TaskValidator.validate(task().copy(tags = (1..21).map { "t$it" }), now)
        }
        assertFailsWith<TaskValidationError.TagLength> {
            TaskValidator.validate(task().copy(tags = listOf("x".repeat(31))), now)
        }
    }

    @Test
    fun `subtask caps 50 items and 1-100 title`() {
        assertFailsWith<TaskValidationError.TooManySubtasks> {
            TaskValidator.validate(task().copy(subtasks = (1..51).map { Subtask("id$it", "s") }), now)
        }
        assertFailsWith<TaskValidationError.SubtaskTitleLength> {
            TaskValidator.validate(task().copy(subtasks = listOf(Subtask("abcd", ""))), now)
        }
    }

    @Test
    fun `dependency cap 50`() {
        assertFailsWith<TaskValidationError.TooManyDependencies> {
            TaskValidator.validate(task().copy(dependencies = (1..51).map { "dep-$it" }), now)
        }
    }

    @Test
    fun `estimate 0 coerces to unset, range 1-10080 enforced`() {
        assertEquals(null, TaskValidator.validate(task().copy(estimatedMinutes = 0), now).estimatedMinutes)
        assertFailsWith<TaskValidationError.EstimateRange> {
            TaskValidator.validate(task().copy(estimatedMinutes = 10081), now)
        }
    }

    @Test
    fun `snooze clamped to 365 days out`() {
        val tooFar = now.plus(400, ChronoUnit.DAYS)
        assertEquals(
            now.plus(365, ChronoUnit.DAYS),
            TaskValidator.validate(task().copy(snoozedUntil = tooFar), now).snoozedUntil,
        )
    }

    @Test
    fun `normalize applies the three coercions`() {
        val raw = task().copy(
            tags = listOf(" Auth", "AUTH", "work", "  "),
            estimatedMinutes = 0,
            snoozedUntil = now.plus(400, ChronoUnit.DAYS),
        )

        val normalized = TaskValidator.normalize(raw, now)

        assertEquals(listOf("auth", "work"), normalized.tags)
        assertEquals(null, normalized.estimatedMinutes)
        assertEquals(now.plus(365, ChronoUnit.DAYS), normalized.snoozedUntil)
    }

    @Test
    fun `normalize never rejects, so a state change works on a synced task that breaks a limit`() {
        // iOS counts characters, so 79 letters plus one emoji is a legal title there. It is 81 UTF-16 units.
        val overLimit = task(title = "x".repeat(79) + "\uD83D\uDC4D").copy(
            description = "d".repeat(601),
            tags = List(21) { "tag-$it" },
            estimatedMinutes = 10_081,
        )

        val normalized = TaskValidator.normalize(overLimit, now)

        assertEquals(overLimit, normalized) // nothing to coerce, and nothing is truncated
        assertFailsWith<TaskValidationError.TitleLength> { TaskValidator.validate(overLimit, now) }
    }

    @Test
    fun `id shorter than 4 rejected`() {
        assertFailsWith<TaskValidationError.IdTooShort> { TaskValidator.validate(task().copy(id = "abc"), now) }
    }

    @Test
    fun `time entry caps`() {
        assertFailsWith<TaskValidationError.NoteLength> {
            TaskValidator.validate(
                task().copy(timeEntries = listOf(TimeEntry("abcd1234", now, now, notes = "n".repeat(201)))),
                now,
            )
        }
    }
}
