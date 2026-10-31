package dev.vinny.gsd.sync

import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.store.SyncOp
import dev.vinny.gsd.store.SyncQueueEntity
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncResult(
    val status: String, // success | partial | error
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
    val failedItems: Int,
    val error: String? = null,
    /** True when the run did nothing because no owner was signed in. */
    val notSignedIn: Boolean = false,
) {
    /**
     * The run pulled, pushed, and reconciled without an error, though some pushes may still be failing
     * ("partial"). Work that must see data at least as fresh as the server, such as the auto-archive
     * sweep, waits for a clean run (parity backlog B-46).
     */
    val clean: Boolean get() = error == null
}

class MalformedRemoteVersion : Exception("Remote task version is missing or malformed")

/** No owner resolved: a guest listing is empty and must never drive pull or reconciliation (spec §9.4). */
class NotSignedIn : Exception("Not signed in")

/**
 * The account this run started for signed out, or another signed in, before a write (parity backlog B-14,
 * web #538). The run stops where it is: nothing pulled lands, nothing is deleted, and no retry is spent.
 */
class StaleSyncSession : Exception("Sync session ended before this write")

/**
 * The last-write-wins sync engine (spec §9.3–§9.5): pull → push → deletion
 * reconciliation, one run at a time. Every rule here is exercised by JVM
 * tests over fakes — this class must stay free of Android imports.
 */
class SyncEngine(
    private val remote: SyncRemote,
    private val local: SyncLocalStore,
    private val clock: Clock,
    private val throttleMs: Long = 100,
    private val ownerId: suspend () -> String? = { null },
) {
    companion object {
        /**
         * Bump when a build first reads a new synced field, in step with the web's
         * `SYNC_CONFIG.PULL_CURSOR_VERSION` and iOS `SyncCursor.contractVersion`. 3 is `saved_at`.
         */
        const val PULL_CONTRACT_VERSION = 3
        private const val EPOCH_CURSOR = "1970-01-01 00:00:00.000Z"
    }

    private val mutex = Mutex()

    private val backoffMs = longArrayOf(5_000, 10_000, 30_000, 60_000, 300_000)

    suspend fun runSync(triggeredByUser: Boolean): SyncResult = mutex.withLock {
        val startedAt = clock.instant()
        var pulled = 0
        var conflicts = 0
        var pushed = 0
        var pushFailures = 0
        var error: String? = null
        var notSignedIn = false
        try {
            val owner = ownerId() ?: throw NotSignedIn()
            val pullOutcome = pull(owner)
            pulled = pullOutcome.first
            conflicts = pullOutcome.second

            val pushOutcome = push(owner)
            pushed = pushOutcome.first
            pushFailures = pushOutcome.second

            reconcileDeletions(owner)
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
            // A token the server rejects is a dead session, however long before it expires (review S1)
            notSignedIn = failure is NotSignedIn || failure is PocketBaseApi.Unauthorized
        }
        val failedCount = local.failedQueue().size
        val status = when {
            error != null -> "error"
            pushFailures > 0 || failedCount > 0 -> "partial"
            else -> "success"
        }
        val result = SyncResult(status, pushed, pulled, conflicts, failedCount, error, notSignedIn)
        local.insertHistory(
            SyncHistoryEntry(
                timestamp = startedAt.toEpochMilli(),
                status = status,
                pushedCount = pushed,
                pulledCount = pulled,
                conflictsResolved = conflicts,
                failedCount = failedCount,
                errorMessage = error,
                durationMs = clock.instant().toEpochMilli() - startedAt.toEpochMilli(),
                deviceId = local.deviceId(),
                triggeredBy = if (triggeredByUser) "user" else "auto",
            ),
        )
        result
    }

    /**
     * Checked just before each write rather than inside its Room transaction: the session lives in the
     * token store, not the database, so a sign-out can still land between the check and one write.
     */
    private suspend fun fence(owner: String) {
        if (ownerId() != owner) throw StaleSyncSession()
    }

    private suspend fun pull(owner: String): Pair<Int, Int> {
        var pulled = 0
        var conflicts = 0
        var maxApplied: Instant? = null
        val now = clock.instant()
        val stored = local.cursor()
        // An older build's cursor sits past rows it stored without a field this build reads: re-pull once
        // and merge same-version rows, before any push could send that field empty (spec 2026-10-29 §2).
        val backfill = stored != null && local.pullContractVersion() < PULL_CONTRACT_VERSION
        val cursor = if (backfill) EPOCH_CURSOR else stored ?: EPOCH_CURSOR
        BoundedListing.collect(cursor, remote::listTasks) { result ->
            result.items.forEachIndexed { index, wire ->
                val existing = local.taskById(wire.taskId)
                val mapped = WireMapper.pull(wire, existing) ?: return@forEachIndexed
                fence(owner) // resolving a tombstone can remove it, and that's a write to this account's data
                if (!resolveTombstone(local, mapped.id, mapped.updatedAt, now)) {
                    return@forEachIndexed // archived/trashed locally, remote not strictly newer (parity spec P§4)
                }
                val incoming = mapped.copy(updatedAt = RemoteStamp.capped(mapped.updatedAt, now))
                val localWriteWins = RemoteStamp.isPastCeiling(mapped.updatedAt, now) && local.hasQueuedWrite(mapped.id)
                if (backfill && existing != null && lacksPlacement(existing, incoming, localWriteWins)) {
                    fence(owner)
                    local.upsertFromRemote(existing.copy(savedAt = incoming.savedAt))
                    return@forEachIndexed
                }
                if (localWriteWins) return@forEachIndexed // the push below heals the far-future record
                val apply = when {
                    existing == null -> true
                    incoming.updatedAt.toEpochMilli() > existing.updatedAt.toEpochMilli() -> {
                        conflicts++ // remote won over a local copy — informational (spec §9.7)
                        true
                    }
                    backfill && incoming.updatedAt.toEpochMilli() == existing.updatedAt.toEpochMilli() ->
                        true // the same version: merge what an older build dropped, not a conflict
                    else -> false // equal or local newer → no-op (spec §9.3)
                }
                if (apply) {
                    fence(owner)
                    local.upsertFromRemote(incoming)
                    pulled++
                    PbDates.parse(result.updatedStamps.getOrNull(index))?.let { stamp ->
                        if (maxApplied == null || stamp.isAfter(maxApplied)) maxApplied = stamp
                    }
                }
            }
        }
        fence(owner)
        maxApplied?.let { applied ->
            val next = PbDates.cursorAfter(applied)
            if (!backfill || next > stored!!) local.saveCursor(next) // a backfill never moves the cursor back
        }
        if (stored == null || backfill) local.savePullContractVersion(PULL_CONTRACT_VERSION)
        return pulled to conflicts
    }

    /**
     * An older build dropped `saved_at`, and the local copy is about to win: it's newer, or it has a queued
     * write against a far-future remote. The push would then send an empty placement. Backfill only:
     * outside it, the same shape is a real Return.
     */
    private fun lacksPlacement(existing: Task, incoming: Task, localWriteWins: Boolean): Boolean =
        existing.savedAt == null && incoming.savedAt != null &&
            (localWriteWins || incoming.updatedAt.toEpochMilli() < existing.updatedAt.toEpochMilli())

    private suspend fun fetchRemoteIndex(): MutableMap<String, Pair<String, Instant?>> =
        BoundedListing.collect("1970-01-01 00:00:00.000Z", remote::listTasks)
            .associateTo(mutableMapOf()) { wire -> wire.taskId to (wire.id to PbDates.parse(wire.clientUpdatedAt)) }

    /** Returns pushed count to permanently-failed-this-drain count. */
    private suspend fun push(owner: String): Pair<Int, Int> {
        val pending = local.pendingQueue().sortedBy { it.id }
        // The index is for the queue. With nothing queued, reconciliation's listing is the only one (§9.5).
        if (pending.isEmpty()) return 0 to 0
        val deviceId = local.deviceId()
        val index = fetchRemoteIndex()
        var pushed = 0
        var failures = 0
        val now = clock.instant()
        for (item in pending) {
            fence(owner) // outside the item's try: a session that ended must not spend this item's retries
            if (item.retryCount > 0) {
                val waitMs = backoffMs[(item.retryCount - 1).coerceAtMost(backoffMs.lastIndex)]
                val lastAttempt = item.lastAttemptAt ?: 0L
                if (now.toEpochMilli() < lastAttempt + waitMs) continue // backoff gate
            }
            try {
                when (item.operation) {
                    SyncOp.CREATE, SyncOp.UPDATE -> {
                        val payload = item.payloadJson?.let { raw ->
                            TaskJson.decodeOrNull(TaskJson.json.parseToJsonElement(raw))
                        }
                        if (payload == null) {
                            local.markQueueItemDone(item.id)
                            continue
                        }
                        val remoteEntry = index[item.taskId]
                        if (remoteEntry != null && remoteEntry.second == null) {
                            // a missing or malformed remote version may protect, never authorize overwrite (S§8.2)
                            throw MalformedRemoteVersion()
                        }
                        val remoteStamp = remoteEntry?.second
                        if (remoteStamp != null && RemoteStamp.isNewer(remoteStamp, payload.updatedAt.toEpochMilli(), now)) {
                            local.markQueueItemDone(item.id) // LWW payload guard: pull delivers remote (spec §9.3)
                            continue
                        }
                        // Placement comes from the current row: a payload queued by an older build has none
                        val current = local.taskById(item.taskId)
                        val wire = if (current != null) {
                            WireMapper.push(payload.copy(savedAt = current.savedAt), deviceId, owner)
                        } else {
                            WireMapper.push(payload, deviceId, owner, sendSavedAt = false)
                        }
                        if (remoteEntry != null) {
                            remote.updateTask(remoteEntry.first, wire)
                        } else {
                            val created = remote.createTask(wire)
                            index[item.taskId] = created.id to PbDates.parse(created.clientUpdatedAt)
                        }
                        local.markQueueItemDone(item.id)
                        pushed++
                    }
                    SyncOp.DELETE -> {
                        val remoteStamp = index[item.taskId]?.second
                        if (remoteEditedAfterArchive(item, remoteStamp, now) || remoteEditedAfterQueued(item, remoteStamp, now)) {
                            local.markQueueItemDone(item.id) // resolved: the newer remote edit stays (B-42, B-47)
                            continue
                        }
                        index[item.taskId]?.let { remote.deleteTask(it.first) }
                        index.remove(item.taskId)
                        local.markQueueItemDone(item.id)
                        pushed++
                    }
                }
                if (throttleMs > 0) delay(throttleMs)
            } catch (rateLimit: PocketBaseApi.RateLimited) {
                return pushed to failures // abort the whole push loop; items stay pending (spec §9.5)
            } catch (failure: Exception) {
                val newCount = item.retryCount + 1
                val nowFailed = newCount >= 5
                if (nowFailed) failures++
                local.bumpQueueRetry(
                    item.id,
                    failure.message ?: failure.javaClass.simpleName,
                    now.toEpochMilli(),
                    nowFailed,
                )
            }
        }
        return pushed to failures
    }

    /** Any delete, Trash or manual, is stale once the server copy changed after it was queued (B-42). */
    private fun remoteEditedAfterQueued(item: SyncQueueEntity, remoteStamp: Instant?, now: Instant): Boolean =
        remoteStamp != null && RemoteStamp.isNewer(remoteStamp, item.timestamp, now)

    /**
     * An archive's delete carries the task as it was archived (spec §9.5). A remote newer than that
     * version means another device edited the task after this copy last synced: a pull missed the
     * edit, or it landed between the pull and the archive. Archiving the stale copy must not delete it
     * (parity backlog B-47). A delete with no payload (trash) and a remote with no usable version keep
     * the plain delete.
     */
    private fun remoteEditedAfterArchive(item: SyncQueueEntity, remoteStamp: Instant?, now: Instant): Boolean {
        if (remoteStamp == null) return false
        val archived = item.payloadJson?.let { raw -> TaskJson.decodeOrNull(TaskJson.json.parseToJsonElement(raw)) }
            ?: return false
        return RemoteStamp.isNewer(remoteStamp, archived.updatedAt.toEpochMilli(), now)
    }

    /**
     * Destructive, runs LAST; safe only because of enqueue-before-write (spec §9.4).
     * Three verdicts per task absent from the remote index (S§8.3): a pending queue
     * row PROTECTs it (still trying to push); a failed-only row means the content
     * never reached the server, so it is ABANDONed to trash instead of erased; no
     * queue row at all means the remote delete already landed, so it is APPLYed locally.
     * A guest listing is empty too, so ABANDON and APPLY wait for the server to confirm the
     * owner in this same run. A steady-state run with nothing to reconcile makes no extra request.
     */
    private suspend fun reconcileDeletions(owner: String) {
        val remoteIds = fetchRemoteIndex().keys
        // Tasks before queues: a task and its queue row commit together, so any task in this snapshot
        // has its row visible to the reads below. The other order deletes a task captured in between.
        val active = local.activeTasks()
        val pending = local.pendingQueue().map { it.taskId }.toSet()
        val failed = local.failedQueue().map { it.taskId }.toSet()
        val now = clock.instant().toEpochMilli()
        val unprotected = active.filter { it.id !in remoteIds && it.id !in pending } // pending → PROTECT
        if (unprotected.isEmpty()) return
        remote.confirmIdentity(owner)
        unprotected.forEach { task ->
            fence(owner)
            if (task.id in failed) local.abandonToTrash(task.id, now) // ABANDON
            else local.deleteFromRemote(task.id)                      // APPLY
        }
    }

    suspend fun requeueFailed() = local.requeueFailed()
}
