package dev.vinny.gsd.sync

import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.SyncQueueEntity

/** Remote surface the engine drives — PocketBaseApi implements it; tests fake it. */
interface SyncRemote {
    suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage
    suspend fun createTask(wire: WireTask): WireTask
    suspend fun updateTask(recordId: String, wire: WireTask): WireTask
    suspend fun deleteTask(recordId: String)

    /**
     * Returns only when the server recognizes this session as [ownerId]. A guest or another
     * account throws, so an empty listing is never mistaken for an empty account (spec §9.4).
     */
    suspend fun confirmIdentity(ownerId: String)
}

/** The server answered the identity check with a different account than the local owner. */
class IdentityMismatch : Exception("Server session belongs to a different account")

/** The two account-level calls deletion needs, separated from the task listing (S§8.4). */
interface AccountRemote {
    /** `DELETE /api/gsd/account`; throws `PocketBaseApi.HttpError(404, …)` where the hook is not deployed. */
    suspend fun deleteAccountViaRoute()
    suspend fun deleteUser(userId: String)
}

sealed interface DeleteAccountOutcome {
    data object Deleted : DeleteAccountOutcome
    /** Nothing was deleted; the listing (or the route's transport) failed first. */
    data object ListingFailed : DeleteAccountOutcome
    /** Some tasks were deleted, then the session was rejected; the account survives. */
    data object PartialAuthRejected : DeleteAccountOutcome
    /** Some tasks were deleted, then another failure or the pass budget ran out; the account survives. */
    data object PartialRetry : DeleteAccountOutcome
    /** Cloud account was deleted and the queue/session already cleared, but the local erase step threw. */
    data object LocalEraseIncomplete : DeleteAccountOutcome
}

data class SyncHistoryEntry(
    val timestamp: Long,
    val status: String,
    val pushedCount: Int,
    val pulledCount: Int,
    val conflictsResolved: Int,
    val failedCount: Int,
    val errorMessage: String?,
    val durationMs: Long,
    val deviceId: String,
    val triggeredBy: String,
)

/**
 * Local surface the engine drives. Remote-sourced writes bypass the TaskStore
 * mutation path on purpose: no re-stamping, no queue enqueue (spec §9.4's
 * enqueue-before-write applies to LOCAL mutations only).
 */
interface SyncLocalStore {
    suspend fun activeTasks(): List<Task>
    suspend fun taskById(id: String): Task?
    suspend fun upsertFromRemote(task: Task)
    suspend fun deleteFromRemote(id: String)

    /** ABANDON verdict (S§8.3): the content never reached the server — keep it recoverable. */
    suspend fun abandonToTrash(id: String, deletedAtMs: Long)
    suspend fun pendingQueue(): List<SyncQueueEntity>
    suspend fun failedQueue(): List<SyncQueueEntity>
    suspend fun markQueueItemDone(id: Long)
    suspend fun bumpQueueRetry(id: Long, error: String, attemptAtMs: Long, nowFailed: Boolean)
    suspend fun requeueFailed()
    suspend fun cursor(): String?
    suspend fun saveCursor(cursor: String)
    suspend fun deviceId(): String
    suspend fun insertHistory(entry: SyncHistoryEntry)

    /** archivedAt/deletedAt in epoch millis, or null when the id is not tombstoned. */
    suspend fun tombstoneAtMs(id: String): Long?

    /** Remove the id from whichever tombstone store holds it (archive or trash). */
    suspend fun removeTombstone(id: String)
}

enum class SyncHealth { OFFLINE, SESSION_EXPIRED, FAILED_ITEMS, STALE_QUEUE, OK }

/** §9.7 health precedence: offline → expired → failed → stale (>1 h) → OK. */
object Health {
    fun evaluate(
        offline: Boolean,
        tokenExpiresAtMs: Long?,
        nowMs: Long,
        failedCount: Int,
        oldestPendingAgeMs: Long?,
    ): SyncHealth = when {
        offline -> SyncHealth.OFFLINE
        tokenExpiresAtMs != null && tokenExpiresAtMs <= nowMs -> SyncHealth.SESSION_EXPIRED
        failedCount > 0 -> SyncHealth.FAILED_ITEMS
        oldestPendingAgeMs != null && oldestPendingAgeMs > 60L * 60 * 1000 -> SyncHealth.STALE_QUEUE
        else -> SyncHealth.OK
    }
}
