package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals

class EscalationWindowTest {
    @Test
    fun `offers Off, 1, 2, 3, and 7 days`() {
        assertEquals(listOf(null, 1, 2, 3, 7), EscalationWindow.OPTIONS)
    }

    @Test
    fun `nothing stored defaults to 2 days`() {
        assertEquals(2, EscalationWindow.fromStored(null))
    }

    @Test
    fun `an unknown stored value falls back to 2 days`() {
        assertEquals(2, EscalationWindow.fromStored(5))
        assertEquals(2, EscalationWindow.fromStored(-3))
    }

    @Test
    fun `every option survives a round trip, Off included`() {
        EscalationWindow.OPTIONS.forEach { option ->
            assertEquals(option, EscalationWindow.fromStored(EscalationWindow.toStored(option)))
        }
    }
}
