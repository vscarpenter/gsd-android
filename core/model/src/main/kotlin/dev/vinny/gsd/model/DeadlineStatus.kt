package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneId

/**
 * Where a due date sits relative to today (S§2.1). Day-based in the injected zone so it
 * agrees with `Analytics.overdueCount`, `FilterCriteria.overdue`, and `TaskCard.dueLabel`:
 * only dates before the START of today are overdue; a task due today is TODAY, never overdue.
 */
enum class DeadlineStatus {
    OVERDUE, TODAY, UPCOMING;

    companion object {
        fun of(due: Instant, now: Instant, zone: ZoneId): DeadlineStatus {
            val dueDay = due.atZone(zone).toLocalDate()
            val today = now.atZone(zone).toLocalDate()
            return when {
                dueDay.isBefore(today) -> OVERDUE
                dueDay == today -> TODAY
                else -> UPCOMING
            }
        }
    }
}
