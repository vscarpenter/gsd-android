package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sweep session (spec-weekly-sweep.md §4) and the actions each card offers (§5). */
class SweepSessionTest {
    private val t0 = Instant.parse("2026-11-01T00:00:00Z")
    private val cards = listOf("a", "b", "c", "d").map { SweepCard(it, SweepReason.STALE) }

    private fun task(urgent: Boolean = false, important: Boolean = true) =
        Task(id = "task-x", title = "x", urgent = urgent, important = important, createdAt = t0, updatedAt = t0)

    // --- session ---

    @Test
    fun `the first card is shown when it still qualifies`() {
        assertEquals(0, SweepSession.firstIndex(cards, setOf("a", "b", "c", "d")))
    }

    @Test
    fun `a card whose task no longer qualifies is skipped without a word`() {
        assertEquals(2, SweepSession.nextIndex(cards, after = 0, stillQualifying = setOf("a", "c", "d")))
    }

    @Test
    fun `running out of cards returns the size, which is the end`() {
        assertEquals(4, SweepSession.nextIndex(cards, after = 2, stillQualifying = setOf("a", "b", "c")))
        assertEquals(4, SweepSession.nextIndex(cards, after = 3, stillQualifying = setOf("a", "b", "c", "d")))
    }

    @Test
    fun `an empty sweep starts at its end`() {
        assertEquals(0, SweepSession.firstIndex(emptyList(), emptySet()))
    }

    @Test
    fun `an unfinished session stamps nothing, a finished one does`() {
        assertEquals(false, SweepSession.stampsLastSwept(total = 3, reachedEnd = false, tappedDone = false))
        assertEquals(true, SweepSession.stampsLastSwept(total = 3, reachedEnd = true, tappedDone = false))
        assertEquals(true, SweepSession.stampsLastSwept(total = 3, reachedEnd = false, tappedDone = true))
        // With no cards the end shows at once and stamps nothing
        assertEquals(false, SweepSession.stampsLastSwept(total = 0, reachedEnd = true, tappedDone = false))
    }

    // --- actions (§5) ---

    @Test
    fun `overdue offers Complete, New date, Mark urgent, Save, Delete, Skip`() {
        assertEquals(
            listOf(
                SweepAction.COMPLETE, SweepAction.NEW_DATE, SweepAction.MARK_URGENT,
                SweepAction.SAVE_FOR_LATER, SweepAction.DELETE, SweepAction.SKIP,
            ),
            SweepAction.forCard(SweepReason.OVERDUE, task(urgent = false)),
        )
    }

    @Test
    fun `an urgent overdue task isn't offered Mark urgent`() {
        assertEquals(
            listOf(
                SweepAction.COMPLETE, SweepAction.NEW_DATE,
                SweepAction.SAVE_FOR_LATER, SweepAction.DELETE, SweepAction.SKIP,
            ),
            SweepAction.forCard(SweepReason.OVERDUE, task(urgent = true)),
        )
    }

    @Test
    fun `escalating offers Mark urgent, New date, Complete, Skip`() {
        assertEquals(
            listOf(SweepAction.MARK_URGENT, SweepAction.NEW_DATE, SweepAction.COMPLETE, SweepAction.SKIP),
            SweepAction.forCard(SweepReason.ESCALATING, task()),
        )
    }

    @Test
    fun `stale offers Complete, Move, New date, Save, Delete, Skip`() {
        assertEquals(
            listOf(
                SweepAction.COMPLETE, SweepAction.MOVE_TO_QUADRANT, SweepAction.NEW_DATE,
                SweepAction.SAVE_FOR_LATER, SweepAction.DELETE, SweepAction.SKIP,
            ),
            SweepAction.forCard(SweepReason.STALE, task()),
        )
    }

    @Test
    fun `saved offers Return, Delete, Skip`() {
        assertEquals(
            listOf(SweepAction.RETURN_TO_MATRIX, SweepAction.DELETE, SweepAction.SKIP),
            SweepAction.forCard(SweepReason.SAVED, task().copy(savedAt = t0)),
        )
    }

    @Test
    fun `undated Schedule offers Add date, Move, Save, Skip`() {
        assertEquals(
            listOf(SweepAction.ADD_DATE, SweepAction.MOVE_TO_QUADRANT, SweepAction.SAVE_FOR_LATER, SweepAction.SKIP),
            SweepAction.forCard(SweepReason.UNDATED_SCHEDULE, task()),
        )
    }

    @Test
    fun `Save for later isn't offered when the task can't be saved`() {
        val running = task().copy(timeEntries = listOf(TimeEntry(id = "te-1", startedAt = t0)))
        assertEquals(false, SavedPlacement.canSave(running))
        assertEquals(
            listOf(SweepAction.COMPLETE, SweepAction.MOVE_TO_QUADRANT, SweepAction.NEW_DATE, SweepAction.DELETE, SweepAction.SKIP),
            SweepAction.forCard(SweepReason.STALE, running),
        )
    }

    @Test
    fun `every card can be skipped`() {
        SweepReason.entries.forEach { reason ->
            val task = if (reason == SweepReason.SAVED) task().copy(savedAt = t0) else task()
            assertEquals(SweepAction.SKIP, SweepAction.forCard(reason, task).last())
        }
    }
}
