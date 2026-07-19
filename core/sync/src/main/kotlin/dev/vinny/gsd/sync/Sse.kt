package dev.vinny.gsd.sync

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
