package dev.vinny.gsd.model

/** What a sweep card can do (spec-weekly-sweep.md §5). Each one calls an action that already exists. */
enum class SweepAction {
    COMPLETE, NEW_DATE, ADD_DATE, MARK_URGENT, MOVE_TO_QUADRANT, SAVE_FOR_LATER, RETURN_TO_MATRIX, DELETE, SKIP;

    companion object {
        /**
         * The actions for one card, in the order the spec lists them. Mark urgent shows only for a task that
         * isn't urgent, and Save for later only when the task can be saved (no running timer, not completed).
         */
        fun forCard(reason: SweepReason, task: Task): List<SweepAction> {
            val markUrgent = SweepAction.MARK_URGENT.takeIf { !task.urgent }
            val save = SAVE_FOR_LATER.takeIf { SavedPlacement.canSave(task) }
            return when (reason) {
                SweepReason.OVERDUE -> listOfNotNull(COMPLETE, NEW_DATE, markUrgent, save, DELETE, SKIP)
                SweepReason.ESCALATING -> listOfNotNull(markUrgent, NEW_DATE, COMPLETE, SKIP)
                SweepReason.STALE -> listOfNotNull(COMPLETE, MOVE_TO_QUADRANT, NEW_DATE, save, DELETE, SKIP)
                SweepReason.SAVED -> listOf(RETURN_TO_MATRIX, DELETE, SKIP)
                SweepReason.UNDATED_SCHEDULE -> listOfNotNull(ADD_DATE, MOVE_TO_QUADRANT, save, SKIP)
            }
        }
    }
}

/**
 * The session's pure rules (spec-weekly-sweep.md §4). The cards are computed once when the sweep opens
 * and never reordered. Before each card shows, the task is checked again, and one that no longer
 * qualifies is skipped without a word. Indexes run to the card count, and the count itself is the end.
 */
object SweepSession {
    fun firstIndex(cards: List<SweepCard>, stillQualifying: Set<String>): Int = nextIndex(cards, after = -1, stillQualifying)

    fun nextIndex(cards: List<SweepCard>, after: Int, stillQualifying: Set<String>): Int {
        var index = after + 1
        while (index < cards.size && cards[index].id !in stillQualifying) index++
        return index
    }

    /**
     * Reaching the end or tapping Done stamps "last swept". Leaving another way stamps nothing, and so
     * does a sweep with no cards, which shows its end at once.
     */
    fun stampsLastSwept(total: Int, reachedEnd: Boolean, tappedDone: Boolean): Boolean =
        total > 0 && (reachedEnd || tappedDone)
}
