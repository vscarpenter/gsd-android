package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A due date's day and its optional time of day (spec §8.10). A due date at local midnight is "all
 * day", which is what the presets, iOS, and the web write. The editor's time picker adds a time.
 */
object DueTime {
    /** The time of day, or null for an all-day due date. */
    fun timeOf(due: Instant, zone: ZoneId): LocalTime? =
        due.atZone(zone).toLocalTime().takeIf { it != LocalTime.MIDNIGHT }

    /** Moves the due date to [date] and keeps the time of day already set, as iOS's date picker does. */
    fun onDate(due: Instant?, date: LocalDate, zone: ZoneId): Instant =
        date.atTime(due?.let { timeOf(it, zone) } ?: LocalTime.MIDNIGHT).atZone(zone).toInstant()

    /** Sets the time of day on the same local date. Null makes it all day again. */
    fun atTime(due: Instant, time: LocalTime?, zone: ZoneId): Instant =
        due.atZone(zone).toLocalDate().atTime(time ?: LocalTime.MIDNIGHT).atZone(zone).toInstant()
}
