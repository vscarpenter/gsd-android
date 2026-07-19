package dev.vinny.gsd.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Due-date presets resolved in local time (spec §8.10): This week = Friday of
 * the current week (weekend → next Friday); Next week = the Monday strictly
 * after today.
 */
enum class DuePreset {
    NONE, TODAY, THIS_WEEK, NEXT_WEEK;

    fun resolve(today: LocalDate): LocalDate? = when (this) {
        NONE -> null
        TODAY -> today
        THIS_WEEK ->
            if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) {
                today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))
            } else {
                today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
            }
        NEXT_WEEK -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    }
}
