package dev.vinny.gsd.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DuePresetTest {
    private val wednesday = LocalDate.of(2026, 7, 15)
    private val saturday = LocalDate.of(2026, 7, 18)
    private val sunday = LocalDate.of(2026, 7, 19)
    private val monday = LocalDate.of(2026, 7, 13)

    @Test
    fun `none resolves to null`() = assertNull(DuePreset.NONE.resolve(wednesday))

    @Test
    fun `today resolves to today`() = assertEquals(wednesday, DuePreset.TODAY.resolve(wednesday))

    @Test
    fun `this week is friday of the current week`() {
        assertEquals(LocalDate.of(2026, 7, 17), DuePreset.THIS_WEEK.resolve(wednesday))
        assertEquals(LocalDate.of(2026, 7, 17), DuePreset.THIS_WEEK.resolve(monday))
    }

    @Test
    fun `this week from a weekend jumps to next friday`() {
        assertEquals(LocalDate.of(2026, 7, 24), DuePreset.THIS_WEEK.resolve(saturday))
        assertEquals(LocalDate.of(2026, 7, 24), DuePreset.THIS_WEEK.resolve(sunday))
    }

    @Test
    fun `next week is the monday strictly after today`() {
        assertEquals(LocalDate.of(2026, 7, 20), DuePreset.NEXT_WEEK.resolve(wednesday))
        assertEquals(LocalDate.of(2026, 7, 20), DuePreset.NEXT_WEEK.resolve(sunday))
        // From a Monday, "next week" is the following Monday, not today
        assertEquals(LocalDate.of(2026, 7, 20), DuePreset.NEXT_WEEK.resolve(monday))
        assertEquals(DayOfWeek.MONDAY, DuePreset.NEXT_WEEK.resolve(monday)!!.dayOfWeek)
    }
}
