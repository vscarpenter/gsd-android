package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class EscalationReason(val wire: String) {
    OVERDUE("overdue"), DUE_SOON("due-soon");

    companion object {
        fun fromWire(wire: String): EscalationReason = entries.first { it.wire == wire }
    }
}

enum class EscalationTarget(val wire: String, val quadrant: Quadrant) {
    URGENT_IMPORTANT("urgent-important", Quadrant.DO_FIRST),
    URGENT_NOT_IMPORTANT("urgent-not-important", Quadrant.DELEGATE);

    companion object {
        fun fromWire(wire: String): EscalationTarget = entries.first { it.wire == wire }
    }
}

data class EscalationResult(
    val escalating: Boolean,
    val daysUntilDue: Int?,
    val reason: EscalationReason?,
    val target: EscalationTarget?,
)

/**
 * Escalation by due date (spec-escalation.md §3, D-11/D-12): a pure signal that an un-urgent task's
 * due date has come within the window. It never changes a task; Mark urgent is an ordinary edit.
 * Pinned by the shared 38-case corpus, so web, iOS, and Android agree.
 */
object Escalation {
    /** [windowDays] is null when the setting is Off. Calendar days in [zone], never elapsed time. */
    fun evaluate(task: Task, now: Instant, windowDays: Int?, zone: ZoneId): EscalationResult {
        val due = task.dueDate ?: return EscalationResult(false, null, null, null)
        val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), due.atZone(zone).toLocalDate()).toInt()
        val eligible = !task.completed && !task.isSaved && !task.urgent && windowDays != null
        if (!eligible || days > windowDays!!) return EscalationResult(false, days, null, null)
        return EscalationResult(
            escalating = true,
            daysUntilDue = days,
            reason = if (days < 0) EscalationReason.OVERDUE else EscalationReason.DUE_SOON,
            target = if (task.important) EscalationTarget.URGENT_IMPORTANT else EscalationTarget.URGENT_NOT_IMPORTANT,
        )
    }
}
