package dev.vinny.gsd.sync

/** A remote enumeration that could not be trusted end to end; callers must not act on partial data (S§8.1). */
class RemoteListingIncomplete(reason: String) : Exception(reason)

/**
 * Walks every page of a task listing and returns all of it, or throws. A partial,
 * over-cap, or inconsistent listing may never drive deletion or reconciliation.
 */
object BoundedListing {
    const val MAX_REMOTE_TASKS = 10_000

    suspend fun collect(
        cursor: String,
        fetch: suspend (page: Int, cursor: String) -> TaskListPage,
        onPage: suspend (TaskListPage) -> Unit = {},
    ): List<WireTask> {
        val all = mutableListOf<WireTask>()
        val seen = HashSet<String>()
        var expectedTotal: Int? = null
        var page = 1
        while (true) {
            val result = fetch(page, cursor)
            if (result.totalItems > MAX_REMOTE_TASKS) throw RemoteListingIncomplete("totalItems ${result.totalItems} exceeds cap $MAX_REMOTE_TASKS")
            if (result.page != page) throw RemoteListingIncomplete("expected page $page, got ${result.page}")
            if (expectedTotal != null && result.totalItems != expectedTotal) throw RemoteListingIncomplete("totalItems changed mid-listing")
            expectedTotal = result.totalItems
            val isLast = result.page >= result.totalPages
            if (result.items.isEmpty() && !isLast) throw RemoteListingIncomplete("empty page $page before the last page")
            result.items.forEach { wire ->
                if (!seen.add(wire.taskId)) throw RemoteListingIncomplete("duplicate task_id ${wire.taskId}")
            }
            all += result.items
            if (all.size > MAX_REMOTE_TASKS) throw RemoteListingIncomplete("materialized ${all.size} exceeds cap")
            onPage(result)
            if (isLast) break
            page++
        }
        if (all.size != expectedTotal) throw RemoteListingIncomplete("listed ${all.size} of $expectedTotal")
        return all
    }
}
