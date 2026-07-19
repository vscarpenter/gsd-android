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
)

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
) {
    private val mutex = Mutex()

    private val backoffMs = longArrayOf(5_000, 10_000, 30_000, 60_000, 300_000)

    suspend fun runSync(triggeredByUser: Boolean): SyncResult = mutex.withLock {
        val startedAt = clock.instant()
        var pulled = 0
        var conflicts = 0
        var pushed = 0
        var pushFailures = 0
        var error: String? = null
        try {
            val pullOutcome = pull()
            pulled = pullOutcome.first
            conflicts = pullOutcome.second

            val pushOutcome = push()
            pushed = pushOutcome.first
            pushFailures = pushOutcome.second

            reconcileDeletions()
        } catch (failure: Exception) {
            error = failure.message ?: failure.javaClass.simpleName
        }
        val failedCount = local.failedQueue().size
        val status = when {
            error != null -> "error"
            pushFailures > 0 || failedCount > 0 -> "partial"
            else -> "success"
        }
        val result = SyncResult(status, pushed, pulled, conflicts, failedCount, error)
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

    private suspend fun pull(): Pair<Int, Int> {
        var pulled = 0
        var conflicts = 0
        var maxApplied: Instant? = null
        val cursor = local.cursor() ?: "1970-01-01 00:00:00.000Z"
        var page = 1
        while (true) {
            val result = remote.listTasks(page, cursor)
            result.items.forEachIndexed { index, wire ->
                val existing = local.taskById(wire.taskId)
                val incoming = WireMapper.pull(wire, existing) ?: return@forEachIndexed
                val apply = when {
                    existing == null -> true
                    incoming.updatedAt.toEpochMilli() > existing.updatedAt.toEpochMilli() -> {
                        conflicts++ // remote won over a local copy — informational (spec §9.7)
                        true
                    }
                    else -> false // equal or local newer → no-op (spec §9.3)
                }
                if (apply) {
                    local.upsertFromRemote(incoming)
                    pulled++
                    PbDates.parse(result.updatedStamps.getOrNull(index))?.let { stamp ->
                        if (maxApplied == null || stamp.isAfter(maxApplied)) maxApplied = stamp
                    }
                }
            }
            if (result.page >= result.totalPages) break
            page = result.page + 1
        }
        maxApplied?.let { local.saveCursor(PbDates.cursorAfter(it)) }
        return pulled to conflicts
    }

    private suspend fun fetchRemoteIndex(): MutableMap<String, Pair<String, Instant?>> {
        val index = mutableMapOf<String, Pair<String, Instant?>>()
        var page = 1
        while (true) {
            val result = remote.listTasks(page, "1970-01-01 00:00:00.000Z")
            result.items.forEach { wire ->
                index[wire.taskId] = wire.id to PbDates.parse(wire.clientUpdatedAt)
            }
            if (result.page >= result.totalPages) break
            page = result.page + 1
        }
        return index
    }

    /** Returns pushed count to permanently-failed-this-drain count. */
    private suspend fun push(): Pair<Int, Int> {
        val deviceId = local.deviceId()
        val index = fetchRemoteIndex()
        var pushed = 0
        var failures = 0
        val now = clock.instant()
        for (item in local.pendingQueue().sortedBy { it.id }) {
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
                        val remoteStamp = remoteEntry?.second
                        if (remoteStamp != null && remoteStamp.toEpochMilli() > payload.updatedAt.toEpochMilli()) {
                            local.markQueueItemDone(item.id) // LWW payload guard: pull delivers remote (spec §9.3)
                            continue
                        }
                        val wire = WireMapper.push(payload, deviceId)
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

    /** Destructive, runs LAST; safe only because of enqueue-before-write (spec §9.4). */
    private suspend fun reconcileDeletions() {
        val remoteIds = fetchRemoteIndex().keys
        val protected = (local.pendingQueue() + local.failedQueue()).map { it.taskId }.toSet()
        local.activeTasks()
            .filter { it.id !in remoteIds && it.id !in protected }
            .forEach { local.deleteFromRemote(it.id) }
    }

    suspend fun requeueFailed() = local.requeueFailed()
}
