package dev.vinny.gsd.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * The PocketBase `tasks` record, byte-for-byte per spec §9.1: snake_case
 * everywhere except `startedAt` inside time entries; nil dates written as "";
 * `id` is the server record id (empty on create); `created`/`updated` are
 * decode-only system autodates.
 */
@Serializable
data class WireTimeEntry(
    val id: String = "",
    @SerialName("startedAt") val startedAt: String = "",
    val minutes: Int = 0,
)

@Serializable
data class WireSubtask(
    val id: String = "",
    val title: String = "",
    val completed: Boolean = false,
)

@Serializable
data class WireTask(
    val id: String = "",
    @SerialName("task_id") val taskId: String,
    val owner: String = "",
    val title: String = "",
    val description: String = "",
    val urgent: Boolean = false,
    val important: Boolean = false,
    val quadrant: String = "",
    @SerialName("due_date") val dueDate: String = "",
    @SerialName("completed_at") val completedAt: String = "",
    @SerialName("last_notification_at") val lastNotificationAt: String = "",
    @SerialName("snoozed_until") val snoozedUntil: String = "",
    /** ISO when Saved, "" to clear (iOS spec §7.1). Null leaves the key off, so the server keeps its value. */
    @SerialName("saved_at") val savedAt: String? = null,
    /** Top three: local `YYYY-MM-DD`, `""` when unset. Null leaves the key off, so the server keeps its pick (C4-6). */
    @SerialName("focus_date") val focusDate: String? = null,
    /** 1 to 3, `0` when unset. Raw, so a fractional or quoted rank reads as no pick rather than failing the record. */
    @SerialName("focus_rank") val focusRank: JsonPrimitive? = null,
    val completed: Boolean = false,
    val recurrence: String = "none",
    val tags: List<String> = emptyList(),
    val subtasks: List<WireSubtask> = emptyList(),
    val dependencies: List<String> = emptyList(),
    @SerialName("notification_enabled") val notificationEnabled: Boolean = true,
    @SerialName("notification_sent") val notificationSent: Boolean = false,
    @SerialName("notify_before") val notifyBefore: Int? = null,
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
    @SerialName("time_spent") val timeSpent: Int = 0,
    @SerialName("time_entries") val timeEntries: List<WireTimeEntry> = emptyList(),
    @SerialName("client_updated_at") val clientUpdatedAt: String = "",
    @SerialName("client_created_at") val clientCreatedAt: String = "",
    @SerialName("device_id") val deviceId: String = "",
    val created: String = "",
    val updated: String = "",
)

object WireJson {
    val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    fun encode(wire: WireTask): String {
        // never write the PocketBase autodates (spec §9.1)
        return json.encodeToString(WireTask.serializer(), wire.copy(created = "", updated = ""))
    }

    fun decodeOrNull(raw: String): WireTask? =
        try {
            json.decodeFromString(WireTask.serializer(), raw)
        } catch (_: Exception) {
            null
        }

    fun decodeOrNull(element: JsonElement): WireTask? =
        try {
            json.decodeFromJsonElement(WireTask.serializer(), element)
        } catch (_: Exception) {
            null
        }
}
