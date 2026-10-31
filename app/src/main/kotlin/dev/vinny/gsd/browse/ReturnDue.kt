package dev.vinny.gsd.browse

import dev.vinny.gsd.model.DuePreset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The due date a Saved task returns with (spec 2026-10-29 §3.3). Presets land on local midnight,
 * which reads as all day (spec §8.10); Keep leaves the stored date and its time alone.
 */
enum class ReturnDue {
    KEEP, NONE, TODAY, THIS_WEEK, NEXT_WEEK;

    fun resolve(current: Instant?, today: LocalDate, zone: ZoneId): Instant? = when (this) {
        KEEP -> current
        NONE -> null
        TODAY -> preset(DuePreset.TODAY, today, zone)
        THIS_WEEK -> preset(DuePreset.THIS_WEEK, today, zone)
        NEXT_WEEK -> preset(DuePreset.NEXT_WEEK, today, zone)
    }

    companion object {
        /** Keep is a choice only when the task has a date to keep. */
        fun choicesFor(current: Instant?): List<ReturnDue> =
            if (current != null) entries.toList() else entries - KEEP

        private fun preset(preset: DuePreset, today: LocalDate, zone: ZoneId): Instant? =
            preset.resolve(today)?.atStartOfDay(zone)?.toInstant()
    }
}
