package dev.vinny.gsd.model

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class SweepReason(val wire: String) {
    OVERDUE("overdue"), ESCALATING("escalating"), STALE("stale"), SAVED("saved"), UNDATED_SCHEDULE("undated-schedule");

    companion object {
        fun fromWire(wire: String): SweepReason = entries.first { it.wire == wire }
    }
}

data class SweepCard(val id: String, val reason: SweepReason)

/**
 * The per-device sweep reminder (spec-weekly-sweep.md §6). [weekday] is ISO: 1 is Monday, 7 is Sunday.
 * Turning it on starts at Sunday 09:00, which is also the stored shape's fallback.
 */
data class SweepReminder(val enabled: Boolean, val weekday: Int, val time: LocalTime) {
    companion object {
        val OFF = SweepReminder(enabled = false, weekday = 7, time = LocalTime.of(9, 0))

        fun timeToStored(time: LocalTime): String = time.toString()

        /** Nothing stored, or any part that doesn't parse, reads as off at the defaults. */
        fun fromStored(enabled: Boolean?, weekday: Int?, time: String?): SweepReminder {
            if (enabled == null || weekday == null || weekday !in 1..7 || time == null) return OFF
            val parsed = try {
                LocalTime.parse(time)
            } catch (_: DateTimeException) {
                return OFF // an unreadable time means the stored reminder isn't one this build wrote
            }
            return SweepReminder(enabled, weekday, parsed)
        }
    }
}

/**
 * The weekly sweep rule (spec-weekly-sweep.md §3, D-14): pure, and pinned by the shared 74-case corpus
 * so web, iOS, and Android agree. Every age counts local calendar days in the zone, never elapsed time.
 */
object WeeklySweep {
    const val STALE_DAYS = 14L
    const val SAVED_DAYS = 30L
    const val ENTRY_DAYS = 7L

    /**
     * Each task gets one card, from the first group it qualifies for. The caller passes active-store rows;
     * completed rows are skipped here too. [windowDays] is null when escalation is Off.
     */
    fun cards(tasks: List<Task>, now: Instant, windowDays: Int?, zone: ZoneId): List<SweepCard> {
        val today = now.atZone(zone).toLocalDate()
        fun daysSince(instant: Instant): Long = ChronoUnit.DAYS.between(instant.atZone(zone).toLocalDate(), today)

        val open = tasks.filter { !it.completed }
        val live = open.filter { !it.isSaved }
        val byDueDate = compareBy<Task> { it.dueDate }.thenByDescending { it.important }.thenBy { it.id }

        val overdue = live.filter { task -> task.dueDate?.let { daysSince(it) > 0 } == true }.sortedWith(byDueDate)
        val escalating = live.filter {
            Escalation.evaluate(it, now, windowDays, zone).reason == EscalationReason.DUE_SOON
        }.sortedWith(byDueDate)
        val stale = live.filter { daysSince(it.updatedAt) >= STALE_DAYS }
            .sortedWith(compareBy<Task> { it.quadrant != Quadrant.ELIMINATE }.thenBy { it.updatedAt }.thenBy { it.id })
        val saved = open.filter { task -> task.savedAt?.let { daysSince(it) >= SAVED_DAYS } == true }
            .sortedWith(compareBy<Task> { it.savedAt }.thenBy { it.id })
        val undated = live.filter { it.quadrant == Quadrant.SCHEDULE && it.dueDate == null }
            .sortedWith(compareBy<Task> { it.createdAt }.thenBy { it.id })

        val seen = mutableSetOf<String>()
        return listOf(
            SweepReason.OVERDUE to overdue,
            SweepReason.ESCALATING to escalating,
            SweepReason.STALE to stale,
            SweepReason.SAVED to saved,
            SweepReason.UNDATED_SCHEDULE to undated,
        ).flatMap { (reason, group) -> group.filter { seen.add(it.id) }.map { SweepCard(it.id, reason) } }
    }

    /** True with at least one card, and the device has never swept or last swept seven or more local days ago. */
    fun entryShown(lastSweptAt: Instant?, cardCount: Int, now: Instant, zone: ZoneId): Boolean {
        if (cardCount < 1) return false
        if (lastSweptAt == null) return true
        val days = ChronoUnit.DAYS.between(lastSweptAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
        return days >= ENTRY_DAYS // a future stamp is negative, so it counts as swept recently
    }

    /**
     * The next occurrence of the weekday and local time strictly after [now], or null when off.
     * ZonedDateTime keeps the wall clock across a change: a time in a gap shifts forward by the gap,
     * and a time in an overlap takes the first occurrence.
     */
    fun nextReminder(reminder: SweepReminder, now: Instant, zone: ZoneId): Instant? {
        if (!reminder.enabled) return null
        val today = now.atZone(zone).toLocalDate()
        return (0L..7L).asSequence()
            .map { today.plusDays(it) }
            .filter { it.dayOfWeek.value == reminder.weekday }
            .map { ZonedDateTime.of(it, reminder.time, zone).toInstant() }
            .first { it.isAfter(now) }
    }
}
