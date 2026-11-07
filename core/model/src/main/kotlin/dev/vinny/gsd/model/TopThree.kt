package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** A task's pick: both set, or both null. [FocusFields.UNSET] is the unset pair. */
data class FocusFields(val focusDate: String?, val focusRank: Int?) {
    companion object {
        val UNSET = FocusFields(null, null)
    }
}

/**
 * Top three (spec-top-three.md §3, D-13): up to three picks for today, chosen by the user. Pure functions,
 * pinned by the shared 123-case corpus so web, iOS, and Android agree. Nothing here writes; the caller
 * applies [tidy]'s writes and the "Add to today" write through the ordinary edit path.
 *
 * Callers pass active-store rows only. Archived and trashed rows live in their own stores.
 */
object TopThree {
    const val MAX_PICKS = 3
    const val CARRY_OVER_DAYS = 7L

    /** The contract that first reads `focus_date` and `focus_rank`. */
    const val FOCUS_CONTRACT = 4

    /** The contract that first reads `saved_at`. */
    const val SAVED_CONTRACT = 3

    data class Picks(val picks: List<String>, val overflow: List<String>, val needsTidy: Boolean)

    data class CarryOver(val fromDate: String?, val ids: List<String>)

    data class TidyWrite(val id: String, val focusDate: String?, val focusRank: Int?)

    /** The wire shape: unset is `""` and `0`, never null, because PocketBase never stores a null number. */
    data class WireFocus(val focusDate: String, val focusRank: Int)

    /** The three fields a pull merge looks at. */
    data class MergeRow(val updatedAt: Instant, val focus: FocusFields, val savedAt: Instant?)

    enum class PullAction(val wire: String) {
        NONE("none"), INSERT("insert"), TAKE_REMOTE("take-remote"), COPY_FIELDS("copy-fields");

        companion object {
            fun fromWire(wire: String): PullAction = entries.first { it.wire == wire }
        }
    }

    data class PullMergeResult(val action: PullAction, val conflict: Boolean, val row: MergeRow?)

    /** Today as the local `YYYY-MM-DD` of [now] in [zone]. */
    fun today(now: Instant, zone: ZoneId): String = now.atZone(zone).toLocalDate().toString()

    /** Eligible: not completed and not Saved. The caller has already kept only active rows. */
    private fun isEligible(task: Task): Boolean = !task.completed && !task.isSaved

    private fun validRank(rank: Int?): Boolean = rank != null && rank in 1..MAX_PICKS

    /** Lowest rank, then newest `updatedAt`, then `id`: the two-device conflict rule (§3.1). */
    private val conflictOrder: Comparator<Task> =
        compareBy<Task> { it.focusRank }.thenByDescending { it.updatedAt }.thenBy { it.id }

    private fun picksOn(tasks: List<Task>, date: String): List<Task> =
        tasks.filter { isEligible(it) && it.focusDate == date && validRank(it.focusRank) }.sortedWith(conflictOrder)

    fun todaysPicks(tasks: List<Task>, now: Instant, zone: ZoneId): Picks {
        val ordered = picksOn(tasks, today(now, zone))
        val ranks = ordered.map { it.focusRank }
        return Picks(
            picks = ordered.take(MAX_PICKS).map { it.id },
            overflow = ordered.drop(MAX_PICKS).map { it.id },
            needsTidy = ordered.size > MAX_PICKS || ranks.size != ranks.toSet().size,
        )
    }

    /** The lowest rank from 1 to 3 that no current pick uses, or null when none is free. */
    fun nextRank(tasks: List<Task>, now: Instant, zone: ZoneId): Int? {
        val used = picksOn(tasks, today(now, zone)).mapNotNull { it.focusRank }.toSet()
        return (1..MAX_PICKS).firstOrNull { it !in used }
    }

    /**
     * What Tidy writes once the user accepts: the first three get ranks 1 to 3, the overflow is cleared,
     * and rows already correct aren't written. Empty when there's no conflict (a gap in ranks is no conflict).
     */
    fun tidy(tasks: List<Task>, now: Instant, zone: ZoneId): List<TidyWrite> {
        val date = today(now, zone)
        if (!todaysPicks(tasks, now, zone).needsTidy) return emptyList()
        return picksOn(tasks, date).mapIndexedNotNull { index, task ->
            val rank = (index + 1).takeIf { it <= MAX_PICKS }
            when {
                rank == null -> TidyWrite(task.id, null, null)
                task.focusRank != rank -> TidyWrite(task.id, date, rank)
                else -> null
            }
        }
    }

    enum class Move { UP, DOWN }

    /** "Add to today" (§3.2): today's date and the lowest free rank, or null when today is full. */
    fun addWrite(tasks: List<Task>, now: Instant, zone: ZoneId): FocusFields? =
        nextRank(tasks, now, zone)?.let { FocusFields(today(now, zone), it) }

    /**
     * "Move up" or "Move down" (§3.2): the pick swaps ranks with its neighbor in the list, so a gap in
     * ranks doesn't matter. Empty at either end, or when [id] isn't one of today's picks.
     */
    fun moveWrites(tasks: List<Task>, id: String, move: Move, now: Instant, zone: ZoneId): List<TidyWrite> {
        val date = today(now, zone)
        val picks = picksOn(tasks, date)
        val from = picks.indexOfFirst { it.id == id }
        val to = if (move == Move.UP) from - 1 else from + 1
        if (from < 0 || to !in picks.indices) return emptyList()
        return listOf(
            TidyWrite(picks[from].id, date, picks[to].focusRank),
            TidyWrite(picks[to].id, date, picks[from].focusRank),
        )
    }

    /**
     * An offer, never a write (§3.4): the latest earlier date, at most seven local days back, that still
     * has an eligible pick, in conflict-rule order and capped at three.
     */
    fun carryOver(tasks: List<Task>, now: Instant, zone: ZoneId): CarryOver {
        val today = now.atZone(zone).toLocalDate()
        val byDate = tasks.filter { isEligible(it) && validRank(it.focusRank) }.mapNotNull { task ->
            val date = parseDate(task.focusDate) ?: return@mapNotNull null
            val daysBack = ChronoUnit.DAYS.between(date, today)
            if (daysBack in 1..CARRY_OVER_DAYS) date to task else null
        }
        val latest = byDate.maxOfOrNull { it.first } ?: return CarryOver(null, emptyList())
        val ids = byDate.filter { it.first == latest }.map { it.second }.sortedWith(conflictOrder)
            .take(MAX_PICKS).map { it.id }
        return CarryOver(latest.toString(), ids)
    }

    /**
     * Eligible tasks that aren't today's picks, in four groups (§3.3): overdue Q1, escalating, Q1 with a
     * due date, then the rest of Q1. [windowDays] is null when escalation is Off.
     */
    fun suggest(tasks: List<Task>, now: Instant, windowDays: Int?, zone: ZoneId): List<String> {
        val today = now.atZone(zone).toLocalDate()
        val picked = picksOn(tasks, today.toString()).map { it.id }.toSet()
        val candidates = tasks.filter { isEligible(it) && it.id !in picked }

        fun daysUntilDue(task: Task): Long? =
            task.dueDate?.let { ChronoUnit.DAYS.between(today, it.atZone(zone).toLocalDate()) }

        val byDueDate = compareBy<Task> { it.dueDate }.thenByDescending { it.important }.thenBy { it.id }
        val q1 = candidates.filter { it.urgent && it.important }
        val overdue = q1.filter { (daysUntilDue(it) ?: 0) < 0 }
        val escalating = candidates.filter { Escalation.evaluate(it, now, windowDays, zone).escalating }
        val dated = q1.filter { it.dueDate != null && (daysUntilDue(it) ?: 0) >= 0 }
        val undated = q1.filter { it.dueDate == null }

        return (
            overdue.sortedWith(byDueDate) + escalating.sortedWith(byDueDate) + dated.sortedWith(byDueDate) +
                undated.sortedWith(compareBy<Task> { it.createdAt }.thenBy { it.id })
            ).map { it.id }
    }

    /** A pull's pair counts only as a real calendar date with an integer rank from 1 to 3 (§3.5). */
    fun fromWire(focusDate: String?, focusRank: Double?): FocusFields {
        val valid = focusRank != null && focusRank % 1.0 == 0.0 && focusRank in 1.0..MAX_PICKS.toDouble() &&
            parseDate(focusDate) != null
        return if (valid) FocusFields(focusDate, focusRank!!.toInt()) else FocusFields.UNSET
    }

    fun toWire(focus: FocusFields): WireFocus {
        val set = focus.focusDate != null && focus.focusRank != null
        return if (set) WireFocus(focus.focusDate!!, focus.focusRank!!) else WireFocus("", 0)
    }

    /**
     * C4-6: a push takes the pick from the row as it is when the queue item drains, never from the queued
     * payload. Null means the row is gone, so both keys stay off the wire and the server keeps its value.
     */
    fun pushFocus(current: FocusFields?): WireFocus? = current?.let(::toWire)

    /**
     * C4-4: one pulled record against the local row. [storedContract] is the pull contract the last
     * completed pull stamped (null counts as older than every field). [tombstoned] means a local archive
     * or trash row exists and the remote isn't strictly newer than it. Below [FOCUS_CONTRACT] a same-version
     * row takes the remote, and a locally newer row copies only the fields this build never knew about.
     */
    fun pullMerge(storedContract: Int?, tombstoned: Boolean, local: MergeRow?, remote: MergeRow): PullMergeResult {
        if (local == null) {
            return if (tombstoned) PullMergeResult(PullAction.NONE, false, null)
            else PullMergeResult(PullAction.INSERT, false, remote)
        }
        if (remote.updatedAt.isAfter(local.updatedAt)) return PullMergeResult(PullAction.TAKE_REMOTE, true, remote)
        val backfill = isBelow(storedContract, FOCUS_CONTRACT)
        if (backfill && remote.updatedAt == local.updatedAt) return PullMergeResult(PullAction.TAKE_REMOTE, false, remote)
        if (!backfill || !remote.updatedAt.isBefore(local.updatedAt)) return PullMergeResult(PullAction.NONE, false, local)

        val copyFocus = local.focus == FocusFields.UNSET && remote.focus != FocusFields.UNSET
        val copySaved = isBelow(storedContract, SAVED_CONTRACT) && local.savedAt == null && remote.savedAt != null
        if (!copyFocus && !copySaved) return PullMergeResult(PullAction.NONE, false, local)
        val merged = local.copy(
            focus = if (copyFocus) remote.focus else local.focus,
            savedAt = if (copySaved) remote.savedAt else local.savedAt,
        )
        return PullMergeResult(PullAction.COPY_FIELDS, false, merged)
    }

    private fun isBelow(stored: Int?, version: Int): Boolean = stored == null || stored < version

    private val DATE_SHAPE = Regex("""\d{4}-\d{2}-\d{2}""")

    /** A real calendar date in `YYYY-MM-DD` form, or null. `2026-02-30` and `2026-11-4` are both null. */
    private fun parseDate(text: String?): LocalDate? {
        if (text == null || !DATE_SHAPE.matches(text)) return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null // the shape fits but the day doesn't exist, like 2026-02-30
        }
    }
}
