package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuadrantTest {
    @Test
    fun `derivation matches the spec truth table`() {
        assertEquals(Quadrant.DO_FIRST, Quadrant.from(urgent = true, important = true))
        assertEquals(Quadrant.SCHEDULE, Quadrant.from(urgent = false, important = true))
        assertEquals(Quadrant.DELEGATE, Quadrant.from(urgent = true, important = false))
        assertEquals(Quadrant.ELIMINATE, Quadrant.from(urgent = false, important = false))
    }

    @Test
    fun `wire ids are authoritative`() {
        assertEquals("urgent-important", Quadrant.DO_FIRST.id)
        assertEquals("not-urgent-important", Quadrant.SCHEDULE.id)
        assertEquals("urgent-not-important", Quadrant.DELEGATE.id)
        assertEquals("not-urgent-not-important", Quadrant.ELIMINATE.id)
    }

    @Test
    fun `display order is Q1 to Q4`() =
        assertEquals(
            listOf(Quadrant.DO_FIRST, Quadrant.SCHEDULE, Quadrant.DELEGATE, Quadrant.ELIMINATE),
            Quadrant.entries.toList(),
        )

    @Test
    fun `fromId round-trips and rejects junk`() {
        Quadrant.entries.forEach { assertEquals(it, Quadrant.fromId(it.id)) }
        assertNull(Quadrant.fromId("yearly-important"))
    }
}
