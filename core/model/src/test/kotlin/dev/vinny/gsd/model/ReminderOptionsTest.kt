package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderOptionsTest {
    private val presets = listOf(0, 5, 15, 30, 60, 120, 1440)

    @Test
    fun `options are the presets when nothing is stored`() =
        assertEquals(presets, ReminderOptions.including(null))

    @Test
    fun `an on-list stored value adds nothing`() {
        assertEquals(presets, ReminderOptions.including(60))
        assertEquals(presets, ReminderOptions.including(1440))
        assertEquals(presets, ReminderOptions.including(0))
    }

    @Test
    fun `an off-list stored value is offered in sorted position, never snapped`() {
        assertEquals(listOf(0, 5, 15, 30, 45, 60, 120, 1440), ReminderOptions.including(45))
        assertEquals(listOf(0, 5, 15, 30, 60, 120, 1440, 2880), ReminderOptions.including(2880))
        assertEquals(listOf(-10, 0, 5, 15, 30, 60, 120, 1440), ReminderOptions.including(-10))
    }

    @Test
    fun `labels follow the shared formula in order`() {
        assertEquals(ReminderLabel.AtTime, ReminderLabel.of(0))
        assertEquals(ReminderLabel.Minutes(1), ReminderLabel.of(1))
        assertEquals(ReminderLabel.Minutes(5), ReminderLabel.of(5))
        assertEquals(ReminderLabel.Minutes(45), ReminderLabel.of(45))
        assertEquals(ReminderLabel.Hours(1), ReminderLabel.of(60))
        assertEquals(ReminderLabel.Minutes(90), ReminderLabel.of(90))     // not 1.5 hours
        assertEquals(ReminderLabel.Hours(2), ReminderLabel.of(120))
        assertEquals(ReminderLabel.Days(1), ReminderLabel.of(1440))
        assertEquals(ReminderLabel.Hours(36), ReminderLabel.of(2160))     // 36 h, not 1.5 days
        assertEquals(ReminderLabel.Days(2), ReminderLabel.of(2880))
    }
}
