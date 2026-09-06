package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmartViewLimitsTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private fun view(
        id: String = "v1", name: String = "Focus", icon: String = "bolt",
        criteria: FilterCriteria = FilterCriteria(),
    ) = SmartView(id, name, icon, criteria, isBuiltIn = false, createdAt = t0, updatedAt = t0)

    @Test
    fun `a well-formed view has no violation`() = assertNull(SmartViewLimits.violation(view()))

    @Test
    fun `each bound names its field`() {
        assertEquals("id", SmartViewLimits.violation(view(id = "")))
        assertEquals("id", SmartViewLimits.violation(view(id = "x".repeat(129))))
        assertEquals("name", SmartViewLimits.violation(view(name = "n".repeat(81))))
        assertEquals("name", SmartViewLimits.violation(view(name = "")))
        assertEquals("icon", SmartViewLimits.violation(view(icon = "i".repeat(65))))
        assertEquals("searchQuery", SmartViewLimits.violation(view(criteria = FilterCriteria(searchQuery = "q".repeat(601)))))
        assertEquals("tags", SmartViewLimits.violation(view(criteria = FilterCriteria(tags = List(201) { "t$it" }))))
        assertEquals("tags", SmartViewLimits.violation(view(criteria = FilterCriteria(tags = listOf("t".repeat(31))))))
        assertNull(SmartViewLimits.violation(view(name = "n".repeat(80), criteria = FilterCriteria(tags = List(200) { "t$it" }))))
    }
}
