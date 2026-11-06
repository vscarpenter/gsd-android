package dev.vinny.gsd.store

import androidx.room.withTransaction

/**
 * A delete that arrived from the server (spec §8.20, §9.4): reconciliation's APPLY verdict and the
 * realtime delete. No Trash copy, since only the deleting device holds one.
 */
suspend fun GsdDatabase.deleteFromRemote(id: String) = withTransaction {
    // The verdict came from an earlier queue read. A pending row now means the user edited the task
    // since, and the push will land it (sprint backlog RECONCILE-RECHECK).
    if (syncQueueDao().countPendingForTask(id) > 0) return@withTransaction
    taskDao().deleteById(id)
}
