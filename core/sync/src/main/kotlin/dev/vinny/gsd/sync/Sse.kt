package dev.vinny.gsd.sync

import dev.vinny.gsd.store.SyncOp
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SseEvent(val id: String?, val event: String?, val data: String)

/**
 * Incremental SSE framing per spec §9.6: `id:`/`event:`/`data:` lines with or
 * without a space after the colon; `:` comment lines are heartbeats; a blank
 * line delimits an event; multiple `data:` lines join with `\n`.
 */
class SseParser {
    private var id: String? = null
    private var event: String? = null
    private val data = mutableListOf<String>()

    fun feedLine(line: String): List<SseEvent> {
        if (line.isEmpty()) {
            if (data.isEmpty() && event == null && id == null) return emptyList()
            val complete = SseEvent(id, event, data.joinToString("\n"))
            id = null
            event = null
            data.clear()
            return listOf(complete)
        }
        if (line.startsWith(":")) return emptyList() // comment heartbeat
        val separator = line.indexOf(':')
        if (separator < 0) return emptyList()
        val field = line.substring(0, separator)
        val value = line.substring(separator + 1).removePrefix(" ")
        when (field) {
            "id" -> id = value
            "event" -> event = value
            "data" -> data += value
        }
        return emptyList()
    }

    companion object {
        fun clientId(event: SseEvent): String? =
            try {
                Json.parseToJsonElement(event.data).jsonObject["clientId"]
                    ?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
    }
}

sealed interface SseDecision {
    data object ApplyUpsert : SseDecision
    data object ApplyDelete : SseDecision
    data class Drop(val reason: String) : SseDecision
}

/**
 * Apply rules for realtime task events (spec §9.6). Owner check fails closed;
 * creates/updates are echo-filtered by device id and blocked by a pending
 * local delete; deletes are protected by ANY pending queue item instead of
 * echo filtering (they carry the last writer's device id).
 */
object SseApplyRules {
    fun decide(
        action: String,
        record: WireTask,
        ownUserId: String,
        ownDeviceId: String,
        pendingDeleteTaskIds: Set<String>,
        pendingAnyTaskIds: Set<String>,
    ): SseDecision {
        if (record.owner.isNotEmpty() && record.owner != ownUserId) {
            return SseDecision.Drop("foreign owner")
        }
        return when (action) {
            "create", "update" -> when {
                record.deviceId == ownDeviceId -> SseDecision.Drop("echo")
                record.taskId in pendingDeleteTaskIds -> SseDecision.Drop("local delete pending")
                else -> SseDecision.ApplyUpsert
            }
            "delete" -> when {
                record.taskId in pendingAnyTaskIds -> SseDecision.Drop("queue item pending")
                else -> SseDecision.ApplyDelete
            }
            else -> SseDecision.Drop("unknown action")
        }
    }
}

/**
 * The web's isRemoteNewerThanArchive rule (parity spec P§4), applied to both
 * tombstone stores: only a remote edit STRICTLY newer than the local
 * archive/trash moment may resurrect a task; equal, older, missing, or
 * unparseable never does — the tombstone stands, fail closed. Shared by
 * SyncEngine's pull-apply loop (same module) and the app's SSE apply path
 * (SyncCoordinator.applyRealtime, a different Gradle module) — public, not
 * internal, so both call sites can reach it.
 */
suspend fun resolveTombstone(local: SyncLocalStore, taskId: String, remoteUpdatedAt: Instant?, now: Instant): Boolean {
    val tombstoneMs = local.tombstoneAtMs(taskId) ?: return true // no tombstone: apply normally
    if (remoteUpdatedAt == null) return false // fail closed
    if (!RemoteStamp.isNewer(remoteUpdatedAt, tombstoneMs, now)) return false // suppressed
    local.removeTombstone(taskId) // deliberate resurrection, exactly once
    return true
}

/**
 * A realtime upsert (spec §9.6, [SseDecision.ApplyUpsert]): the tombstone guard first (parity spec
 * P§4), then last-write-wins against the local copy (spec §9.3). It lives here, not in the app's
 * coordinator, so the guard's wiring has a JVM test. [sessionHolds] is asked before each write,
 * because the reads before it suspend (sprint backlog AND-RT-FENCE).
 */
suspend fun applyRealtimeUpsert(local: SyncLocalStore, record: WireTask, now: Instant, sessionHolds: suspend () -> Boolean) {
    val existing = local.taskById(record.taskId)
    val mapped = WireMapper.pull(record, existing ?: local.buriedTask(record.taskId)) ?: return // merges as the pull does
    if (!sessionHolds()) return // resolving a tombstone can remove it, and that's a write
    if (!resolveTombstone(local, mapped.id, mapped.updatedAt, now)) {
        return // archived/trashed locally, remote not strictly newer (parity spec P§4)
    }
    if (RemoteStamp.isPastCeiling(mapped.updatedAt, now) && local.hasQueuedWrite(mapped.id)) return
    val incoming = mapped.copy(updatedAt = RemoteStamp.capped(mapped.updatedAt, now))
    if (existing == null || incoming.updatedAt.toEpochMilli() > existing.updatedAt.toEpochMilli()) {
        if (!sessionHolds()) return
        local.upsertFromRemote(incoming)
    }
}

/**
 * One realtime event, decided by [SseApplyRules] and applied (spec §9.6). [sessionHolds] says whether
 * the account the event arrived for is still signed in. It is asked again before each write, because
 * the reads before a write suspend and a sign-out can land in between (sprint backlog AND-RT-FENCE).
 * A session that ended drops the event; the next sign-in's pull delivers the record. It lives here,
 * not in the app's coordinator, so the wiring has JVM tests.
 */
suspend fun applyRealtimeEvent(
    local: SyncLocalStore,
    action: String,
    record: WireTask,
    ownUserId: String,
    now: Instant,
    sessionHolds: suspend () -> Boolean,
) {
    val ownDevice = local.deviceId()
    val pending = local.pendingQueue()
    val failed = local.failedQueue()
    val decision = SseApplyRules.decide(
        action = action,
        record = record,
        ownUserId = ownUserId,
        ownDeviceId = ownDevice,
        pendingDeleteTaskIds = pending.filter { it.operation == SyncOp.DELETE }.map { it.taskId }.toSet(),
        pendingAnyTaskIds = (pending + failed).map { it.taskId }.toSet(),
    )
    when (decision) {
        is SseDecision.ApplyUpsert -> applyRealtimeUpsert(local, record, now, sessionHolds)
        is SseDecision.ApplyDelete -> if (sessionHolds()) local.deleteFromRemote(record.taskId)
        is SseDecision.Drop -> Unit
    }
}
