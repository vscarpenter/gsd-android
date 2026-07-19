package dev.vinny.gsd.sync

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * PocketBase date handling (spec §9.1/§9.4): parsing is lenient (space or `T`
 * separator, optional fraction, `Z` or offset); writing is always fractional-ms
 * `T`/`Z`. The pull cursor uses the space-separated system form.
 */
object PbDates {
    private val wireFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private val spaceFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS'Z'")

    fun parse(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().replaceFirst(' ', 'T')
        return try {
            OffsetDateTime.parse(normalized).toInstant()
        } catch (_: Exception) {
            try {
                OffsetDateTime.parse(normalized + "Z").toInstant()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun formatWire(instant: Instant?): String =
        instant?.let { wireFormat.format(it.truncatedTo(ChronoUnit.MILLIS).atZone(ZoneOffset.UTC)) } ?: ""

    fun formatSpace(instant: Instant): String =
        spaceFormat.format(instant.truncatedTo(ChronoUnit.MILLIS).atZone(ZoneOffset.UTC))

    /** Cursor advance with the §9.4 five-second overlap rewind. */
    fun cursorAfter(maxApplied: Instant): String = formatSpace(maxApplied.minusSeconds(5))
}
