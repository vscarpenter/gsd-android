package dev.vinny.gsd.model

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SweepReminderTest {
    @Test
    fun `nothing stored is off, Sunday at 09 00`() {
        assertEquals(SweepReminder.OFF, SweepReminder.fromStored(null, null, null))
        assertEquals(SweepReminder(false, 7, LocalTime.of(9, 0)), SweepReminder.OFF)
    }

    @Test
    fun `a stored reminder round-trips`() {
        val reminder = SweepReminder(true, 1, LocalTime.of(7, 30))
        assertEquals(reminder, SweepReminder.fromStored(true, reminder.weekday, SweepReminder.timeToStored(reminder.time)))
    }

    @Test
    fun `an unknown stored reminder falls back to off`() {
        assertEquals(SweepReminder.OFF, SweepReminder.fromStored(true, 9, "09:00"))
        assertEquals(SweepReminder.OFF, SweepReminder.fromStored(true, 0, "09:00"))
        assertEquals(SweepReminder.OFF, SweepReminder.fromStored(true, 7, "25:99"))
        assertEquals(SweepReminder.OFF, SweepReminder.fromStored(true, 7, "nine"))
        assertEquals(SweepReminder.OFF, SweepReminder.fromStored(true, null, "09:00"))
    }

    @Test
    fun `turning it on starts at Sunday 09 00`() {
        assertEquals(SweepReminder(true, 7, LocalTime.of(9, 0)), SweepReminder.OFF.copy(enabled = true))
    }
}
