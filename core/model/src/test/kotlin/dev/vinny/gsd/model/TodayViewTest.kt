package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the Today section shows (spec-top-three.md §4) and the writes its menu makes (§3.2). */
class TodayViewTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-11-04T16:00:00Z") // 10:00 on 2026-11-04 in Chicago
    private val t0 = Instant.parse("2026-10-01T00:00:00Z")

    private fun task(
        id: String,
        date: String? = null,
        rank: Int? = null,
        urgent: Boolean = true,
        important: Boolean = true,
        completed: Boolean = false,
    ) = Task(
        id = id, title = id, urgent = urgent, important = important, completed = completed,
        createdAt = t0, updatedAt = t0, focusDate = date, focusRank = rank,
    )

    private fun pick(id: String, rank: Int, date: String = "2026-11-04") = task(id, date, rank)

    private fun build(vararg tasks: Task) = TodayView.build(tasks.toList(), now, windowDays = 2, zone)

    @Test
    fun `no picks leaves a free slot and no section rows`() {
        val view = build(task("a"))
        assertEquals(emptyList(), view.picks)
        assertTrue(view.hasFreeSlot)
        assertNull(view.carryOver)
    }

    @Test
    fun `picks come back as tasks in rank order`() {
        val view = build(pick("b", 2), pick("a", 1), task("c"))
        assertEquals(listOf("a", "b"), view.picks.map { it.id })
        assertTrue(view.hasFreeSlot)
    }

    @Test
    fun `three picks leave no free slot and hide the offers`() {
        val view = build(
            pick("a", 1), pick("b", 2), pick("c", 3),
            task("old", "2026-11-03", 1), task("q1"),
        )
        assertEquals(false, view.hasFreeSlot)
        assertNull(view.carryOver)
        assertEquals(emptyList(), view.suggestions)
    }

    @Test
    fun `yesterday's picks are offered while a slot is free`() {
        val view = build(task("a", "2026-11-03", 1), task("b", "2026-11-03", 2))
        assertEquals(LocalDate.of(2026, 11, 3), view.carryOver?.from)
        assertEquals(listOf("a", "b"), view.carryOver?.tasks?.map { it.id })
    }

    @Test
    fun `a carry-over task isn't also suggested, and suggestions stop at three`() {
        val view = build(
            task("carried", "2026-11-03", 1),
            task("q1a"), task("q1b"), task("q1c"), task("q1d"),
        )
        assertEquals(listOf("carried"), view.carryOver?.tasks?.map { it.id })
        assertEquals(3, view.suggestions.size)
        assertTrue("carried" !in view.suggestions.map { it.id })
    }

    @Test
    fun `a conflict is flagged and the extra picks wait`() {
        val view = build(pick("a", 1), pick("b", 1), pick("c", 2), pick("d", 3))
        assertEquals(true, view.needsTidy)
        assertEquals(3, view.picks.size)
    }

    // --- writes (§3.2) ---

    @Test
    fun `add to today takes the lowest free rank on today's date`() {
        val tasks = listOf(pick("a", 1), pick("c", 3), task("x"))
        assertEquals(FocusFields("2026-11-04", 2), TopThree.addWrite(tasks, now, zone))
    }

    @Test
    fun `add to today is refused when today is full`() {
        val tasks = listOf(pick("a", 1), pick("b", 2), pick("c", 3), task("x"))
        assertNull(TopThree.addWrite(tasks, now, zone))
    }

    @Test
    fun `move down swaps ranks with the next pick`() {
        val tasks = listOf(pick("a", 1), pick("b", 2), pick("c", 3))
        assertEquals(
            listOf(TopThree.TidyWrite("a", "2026-11-04", 2), TopThree.TidyWrite("b", "2026-11-04", 1)),
            TopThree.moveWrites(tasks, "a", TopThree.Move.DOWN, now, zone),
        )
    }

    @Test
    fun `move up swaps across a gap in ranks`() {
        val tasks = listOf(pick("a", 1), pick("c", 3))
        assertEquals(
            listOf(TopThree.TidyWrite("c", "2026-11-04", 1), TopThree.TidyWrite("a", "2026-11-04", 3)),
            TopThree.moveWrites(tasks, "c", TopThree.Move.UP, now, zone),
        )
    }

    @Test
    fun `the first pick can't move up and the last can't move down`() {
        val tasks = listOf(pick("a", 1), pick("b", 2))
        assertEquals(emptyList(), TopThree.moveWrites(tasks, "a", TopThree.Move.UP, now, zone))
        assertEquals(emptyList(), TopThree.moveWrites(tasks, "b", TopThree.Move.DOWN, now, zone))
    }

    @Test
    fun `a task that isn't a pick doesn't move`() {
        assertEquals(emptyList(), TopThree.moveWrites(listOf(pick("a", 1), task("x")), "x", TopThree.Move.UP, now, zone))
    }

    // --- the card menu (§4) ---

    @Test
    fun `the menu offers add, remove, or a disabled full`() {
        val open = build(pick("a", 1), task("x"), task("done", completed = true))
        assertEquals(TodayView.MenuAction.REMOVE, open.menuActionFor(open.picks.first()))
        assertEquals(TodayView.MenuAction.ADD, open.menuActionFor(task("x")))
        assertEquals(TodayView.MenuAction.NONE, open.menuActionFor(task("done", completed = true)))
        assertEquals(TodayView.MenuAction.NONE, open.menuActionFor(task("s").copy(savedAt = t0)))

        val full = build(pick("a", 1), pick("b", 2), pick("c", 3), task("x"))
        assertEquals(TodayView.MenuAction.FULL, full.menuActionFor(task("x")))
    }

    @Test
    fun `a pick knows its place and where it can move`() {
        val view = build(pick("a", 1), pick("b", 2), pick("c", 3))
        assertEquals(0, view.positionOf("a"))
        assertEquals(false, view.canMoveUp("a"))
        assertEquals(true, view.canMoveDown("a"))
        assertEquals(true, view.canMoveUp("c"))
        assertEquals(false, view.canMoveDown("c"))
        assertNull(view.positionOf("nope"))
    }

    // --- T6: the free slot comes from nextRank, not from the pick count ---

    @Test
    fun `three picks that leave a rank free still offer a slot`() {
        // Two devices both picked rank 1, so three picks use only ranks 1 and 2
        val view = build(pick("a", 1), pick("b", 1), pick("c", 2))
        assertEquals(3, view.picks.size)
        assertTrue(view.hasFreeSlot)
        assertEquals(TodayView.MenuAction.ADD, view.menuActionFor(task("x")))
        assertEquals(TopThree.nextRank(listOf(pick("a", 1), pick("b", 1), pick("c", 2)), now, zone), 3)
    }

    @Test
    fun `a conflict that uses every rank leaves no slot`() {
        val view = build(pick("a", 1), pick("b", 1), pick("c", 2), pick("d", 3))
        assertEquals(false, view.hasFreeSlot)
        assertEquals(TodayView.MenuAction.FULL, view.menuActionFor(task("x")))
    }

    // --- T7: no moving while the Tidy offer is up ---

    @Test
    fun `no pick can move while a tidy is pending`() {
        val view = build(pick("a", 1), pick("b", 1), pick("c", 2))
        assertTrue(view.needsTidy)
        view.picks.forEach {
            assertEquals(false, view.canMoveUp(it.id), "up for ${it.id}")
            assertEquals(false, view.canMoveDown(it.id), "down for ${it.id}")
        }
    }

    // --- T12: the carry-over label (§4) ---

    private fun carryLabel(from: String) =
        CarryOverOffer(LocalDate.parse(from), emptyList()).label(LocalDate.of(2026, 11, 4))

    @Test
    fun `one day back reads yesterday`() {
        assertEquals(CarryOverLabel.Yesterday, carryLabel("2026-11-03"))
    }

    @Test
    fun `two to six days back name the weekday`() {
        assertEquals(CarryOverLabel.Weekday(java.time.DayOfWeek.MONDAY), carryLabel("2026-11-02"))
        assertEquals(CarryOverLabel.Weekday(java.time.DayOfWeek.FRIDAY), carryLabel("2026-10-30"))
        assertEquals(CarryOverLabel.Weekday(java.time.DayOfWeek.THURSDAY), carryLabel("2026-10-29"))
    }

    @Test
    fun `seven days back reads a week ago, because the weekday would match today's`() {
        assertEquals(CarryOverLabel.AWeekAgo, carryLabel("2026-10-28"))
    }
}
