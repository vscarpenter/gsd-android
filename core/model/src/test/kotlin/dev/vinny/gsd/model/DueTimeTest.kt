package dev.vinny.gsd.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Review finding 6: the editor had no date or time picker, so every due date landed at midnight. */
class DueTimeTest {
    private val zone = ZoneId.of("America/Chicago")
    private fun at(date: String, time: String) =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), zone).toInstant()

    @Test
    fun `a due date at local midnight is all day`() =
        assertNull(DueTime.timeOf(at("2026-09-24", "00:00"), zone))

    @Test
    fun `a due date with a time reports it`() =
        assertEquals(LocalTime.of(15, 30), DueTime.timeOf(at("2026-09-24", "15:30"), zone))

    @Test
    fun `picking a date keeps the time already set`() =
        assertEquals(at("2026-09-30", "15:30"), DueTime.onDate(at("2026-09-24", "15:30"), LocalDate.parse("2026-09-30"), zone))

    @Test
    fun `picking a date for a task with none makes it all day`() =
        assertEquals(at("2026-09-30", "00:00"), DueTime.onDate(null, LocalDate.parse("2026-09-30"), zone))

    @Test
    fun `setting a time keeps the day`() =
        assertEquals(at("2026-09-24", "09:00"), DueTime.atTime(at("2026-09-24", "00:00"), LocalTime.of(9, 0), zone))

    @Test
    fun `clearing the time makes it all day again`() =
        assertEquals(at("2026-09-24", "00:00"), DueTime.atTime(at("2026-09-24", "15:30"), null, zone))
}

class DuePresetSelectionTest {
    private val zone = ZoneId.of("America/Chicago")
    private val today = LocalDate.parse("2026-09-24") // a Thursday

    @Test
    fun `none is selected only without a due date`() {
        assertEquals(true, DuePreset.NONE.isSelectedFor(null, today, zone))
        assertEquals(false, DuePreset.NONE.isSelectedFor(today.atStartOfDay(zone).toInstant(), today, zone))
    }

    @Test
    fun `a preset is selected when the due date falls on its day, at any time`() {
        val todayAfternoon = ZonedDateTime.of(today, LocalTime.of(15, 30), zone).toInstant()
        assertEquals(true, DuePreset.TODAY.isSelectedFor(todayAfternoon, today, zone))
        assertEquals(false, DuePreset.THIS_WEEK.isSelectedFor(todayAfternoon, today, zone))
        val friday = LocalDate.parse("2026-09-25").atStartOfDay(zone).toInstant()
        assertEquals(true, DuePreset.THIS_WEEK.isSelectedFor(friday, today, zone))
    }
}
