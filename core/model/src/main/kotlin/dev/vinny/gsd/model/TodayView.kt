package dev.vinny.gsd.model

import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Picks from an earlier day that are still open, offered again and never written (spec-top-three.md §3.4). */
data class CarryOverOffer(val from: LocalDate, val tasks: List<Task>) {
    /**
     * How the offer is named (§4): one day back is yesterday, two to six days back is the weekday, and seven days
     * back is "a week ago", because that weekday would match today's.
     */
    fun label(today: LocalDate): CarryOverLabel = when (ChronoUnit.DAYS.between(from, today)) {
        1L -> CarryOverLabel.Yesterday
        in 2L..6L -> CarryOverLabel.Weekday(from.dayOfWeek)
        else -> CarryOverLabel.AWeekAgo
    }
}

sealed interface CarryOverLabel {
    data object Yesterday : CarryOverLabel
    data class Weekday(val day: DayOfWeek) : CarryOverLabel
    data object AWeekAgo : CarryOverLabel
}

/**
 * What the Today section draws (spec-top-three.md §4), composed from the pure rules so the screen holds no
 * logic. With a full day there's nothing to offer, so carry-over and suggestions are empty. Suggestions are
 * the first three from the suggestion order once carry-over is taken out.
 */
data class TodayView(
    val picks: List<Task>,
    val needsTidy: Boolean,
    val hasFreeSlot: Boolean,
    val carryOver: CarryOverOffer?,
    val suggestions: List<Task>,
) {
    enum class MenuAction { NONE, ADD, REMOVE, FULL }

    /** Which "Add to today" row a card's menu shows. A completed or Saved task isn't offered one. */
    fun menuActionFor(task: Task): MenuAction = when {
        picks.any { it.id == task.id } -> MenuAction.REMOVE
        task.completed || task.isSaved -> MenuAction.NONE
        hasFreeSlot -> MenuAction.ADD
        else -> MenuAction.FULL
    }

    /** Zero-based place among today's picks, or null when the task isn't one. */
    fun positionOf(id: String): Int? = picks.indexOfFirst { it.id == id }.takeIf { it >= 0 }

    /** Neither move is offered while a tidy is pending: Tidy comes first, so two picks never trade a shared rank. */
    fun canMoveUp(id: String): Boolean = !needsTidy && (positionOf(id) ?: 0) > 0

    fun canMoveDown(id: String): Boolean = !needsTidy && positionOf(id)?.let { it < picks.lastIndex } == true

    companion object {
        fun build(tasks: List<Task>, now: Instant, windowDays: Int?, zone: ZoneId): TodayView {
            val byId = tasks.associateBy { it.id }
            val today = TopThree.todaysPicks(tasks, now, zone)
            val picks = today.picks.mapNotNull(byId::get)
            // From nextRank, as the Add write does: a conflict can show three picks and still leave a rank free
            val hasFreeSlot = TopThree.nextRank(tasks, now, zone) != null
            if (!hasFreeSlot) return TodayView(picks, today.needsTidy, false, null, emptyList())

            val offer = TopThree.carryOver(tasks, now, zone)
            val carried = offer.ids.mapNotNull(byId::get)
            val carryOver = offer.fromDate?.takeIf { carried.isNotEmpty() }?.let { CarryOverOffer(LocalDate.parse(it), carried) }
            val carriedIds = carried.map { it.id }.toSet()
            val suggestions = TopThree.suggest(tasks, now, windowDays, zone)
                .filter { it !in carriedIds }
                .take(TopThree.MAX_PICKS)
                .mapNotNull(byId::get)
            return TodayView(picks, today.needsTidy, true, carryOver, suggestions)
        }
    }
}
