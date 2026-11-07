package dev.vinny.gsd.widget

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** The alarm that redraws the widget at local midnight (spec-top-three.md §9). */
class WidgetMidnightTest {
    private val chicago = ZoneId.of("America/Chicago")

    @Test
    fun `the next midnight is the start of the next local day`() {
        val now = Instant.parse("2026-11-04T16:00:00Z") // 10:00 in Chicago
        assertEquals(Instant.parse("2026-11-05T06:00:00Z"), nextLocalMidnight(now, chicago))
    }

    @Test
    fun `a moment before midnight still points at the coming one`() {
        val now = Instant.parse("2026-11-05T05:59:59Z") // 23:59:59 in Chicago
        assertEquals(Instant.parse("2026-11-05T06:00:00Z"), nextLocalMidnight(now, chicago))
    }

    @Test
    fun `exactly at midnight points at the next day`() {
        val now = Instant.parse("2026-11-05T06:00:00Z")
        assertEquals(Instant.parse("2026-11-06T06:00:00Z"), nextLocalMidnight(now, chicago))
    }

    @Test
    fun `a clock change leaves midnight at local midnight`() {
        // Fall back on 2026-11-01: that day is 25 hours long, so the next midnight is 06:00Z, not 05:00Z
        val now = Instant.parse("2026-11-01T12:00:00Z")
        assertEquals(Instant.parse("2026-11-02T06:00:00Z"), nextLocalMidnight(now, chicago))
    }
}
