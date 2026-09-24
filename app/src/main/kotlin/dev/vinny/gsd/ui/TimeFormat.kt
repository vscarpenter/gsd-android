package dev.vinny.gsd.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A time of day in the user's own clock: 12 or 24 hours as the device is set, in their locale. */
fun formatTimeOfDay(context: Context, time: LocalTime): String {
    val locale = context.resources.configuration.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(time)
}

/** A date and a time to the second, as the sync history lists them, in the user's clock and locale. */
fun formatDateTimeToSecond(context: Context, at: ZonedDateTime): String {
    val locale = context.resources.configuration.locales[0]
    val skeleton = if (DateFormat.is24HourFormat(context)) "MMMdHms" else "MMMdhms"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(at)
}
