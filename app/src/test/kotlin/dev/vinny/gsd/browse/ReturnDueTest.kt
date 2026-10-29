package dev.vinny.gsd.browse

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The due-date choices when a Saved task returns to the matrix (spec 2026-10-29 §3.3). */
class ReturnDueTest {
    private val zone = ZoneId.of("America/Chicago")
    private val today = LocalDate.of(2026, 10, 29) // a Thursday
    private val stored = Instant.parse("2026-10-20T19:30:00.000Z")

    private fun midnight(date: LocalDate): Instant = date.atStartOfDay(zone).toInstant()

    @Test
    fun `keep returns the stored date, time of day included`() {
        assertEquals(stored, ReturnDue.KEEP.resolve(stored, today, zone))
    }

    @Test
    fun `none clears the date`() {
        assertNull(ReturnDue.NONE.resolve(stored, today, zone))
    }

    @Test
    fun `presets land on local midnight, which means all day`() {
        assertEquals(midnight(today), ReturnDue.TODAY.resolve(stored, today, zone))
        assertEquals(midnight(LocalDate.of(2026, 10, 30)), ReturnDue.THIS_WEEK.resolve(stored, today, zone))
        assertEquals(midnight(LocalDate.of(2026, 11, 2)), ReturnDue.NEXT_WEEK.resolve(stored, today, zone))
    }

    @Test
    fun `keep is offered only when there is a date to keep`() {
        assertEquals(ReturnDue.entries.toList(), ReturnDue.choicesFor(stored))
        assertEquals(ReturnDue.entries - ReturnDue.KEEP, ReturnDue.choicesFor(null))
    }
}
