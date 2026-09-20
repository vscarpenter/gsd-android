package dev.vinny.gsd.sync

/**
 * What a confirmed sign-in does to local sync state when the account changed (spec §8.21).
 * A same-owner or first sign-in changes nothing: the cursor still fits that account, and a task
 * deleted on another device while this one was signed out must still delete here.
 */
object OwnerChange {
    suspend fun adopt(
        local: SyncLocalStore,
        previousOwner: String?,
        newOwner: String,
        keepLocalTasks: Boolean,
        nowMs: Long,
    ) {
        if (previousOwner == null || previousOwner == newOwner) return
        local.clearCursor() // the old account's cursor would hide the new account's older records
        if (!keepLocalTasks) return
        // Tasks already synced to the old account have no queue row, so reconciliation would read
        // them as deleted remotely. A pending row protects each one until the push lands it.
        val queued = (local.pendingQueue() + local.failedQueue()).map { it.taskId }.toSet()
        local.activeTasks().filter { it.id !in queued }.forEach { local.enqueueUpdate(it, nowMs) }
    }
}
