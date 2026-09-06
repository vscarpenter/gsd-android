package dev.vinny.gsd.model

/** The shared order for every filtered list — iOS's `TaskFilter` rule (S§3.3). */
object TaskOrdering {
    fun forList(tasks: List<Task>, status: FilterStatus): List<Task> = when (status) {
        FilterStatus.COMPLETED -> tasks.sortedWith(
            // compareByDescending reverses nullsFirst()'s ordering too, so nulls sort last here.
            compareByDescending<Task, java.time.Instant?>(nullsFirst()) { it.completedAt }
                .thenByDescending { it.createdAt },
        )
        else -> tasks.sortedWith(
            compareBy<Task, java.time.Instant?>(nullsLast()) { it.dueDate }
                .thenByDescending { it.createdAt },
        )
    }
}
