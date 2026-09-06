package dev.vinny.gsd.dashboard

import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.QuadrantStat
import kotlin.test.Test
import kotlin.test.assertEquals

class QuadrantCompletionRingsTest {
    @Test
    fun legendValueIsAnEmDashForAnEmptyQuadrant() {
        assertEquals("—", legendValue(QuadrantStat(Quadrant.SCHEDULE, 0, 0)))
    }

    @Test
    fun legendValueIsARoundedPercentForANonEmptyQuadrant() {
        assertEquals("50%", legendValue(QuadrantStat(Quadrant.DO_FIRST, 2, 1)))
    }
}
