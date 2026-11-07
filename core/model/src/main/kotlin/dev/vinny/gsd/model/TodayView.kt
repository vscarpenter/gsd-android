package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Yesterday's (or an earlier day's) unfinished picks, offered and never written (spec-top-three.md §3.4). */
data class CarryOverOffer(val from: LocalDate, val tasks: List<Task>)

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

    fun canMoveUp(id: String): Boolean = (positionOf(id) ?: 0) > 0

    fun canMoveDown(id: String): Boolean = positionOf(id)?.let { it < picks.lastIndex } == true

    companion object {
        fun build(tasks: List<Task>, now: Instant, windowDays: Int?, zone: ZoneId): TodayView {
            val byId = tasks.associateBy { it.id }
            val today = TopThree.todaysPicks(tasks, now, zone)
            val picks = today.picks.mapNotNull(byId::get)
            val hasFreeSlot = picks.size < TopThree.MAX_PICKS
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
