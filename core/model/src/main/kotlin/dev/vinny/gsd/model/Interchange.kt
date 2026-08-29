package dev.vinny.gsd.model

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class ImportResult(val tasks: List<Task>, val skipped: Int)

/**
 * The shared interchange format (P§2.1/§2.2, replacing spec §8.16): a
 * seven-store, versioned envelope, pretty-printed with sorted keys; lenient
 * import with 10,000-task / 10 MB caps.
 */
object Interchange {
    class TooLarge(message: String) : Exception(message)

    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun export(envelope: BackupEnvelope, exportedAt: Instant): String {
        val root = buildJsonObject {
            put("version", JsonPrimitive("2.1.0"))
            put("exportedAt", JsonPrimitive(iso(exportedAt)))
            put("tasks", JsonArray(envelope.tasks.map { taskRecord(it) }))
            envelope.archivedTasks?.let { rows ->
                put("archivedTasks", JsonArray(rows.map { taskRecord(it.task, "archivedAt" to iso(it.archivedAt)) }))
            }
            envelope.deletedTasks?.let { rows ->
                put("deletedTasks", JsonArray(rows.map { taskRecord(it.task, "deletedAt" to iso(it.deletedAt)) }))
            }
            envelope.smartViews?.let { views -> put("smartViews", JsonArray(views.map { smartViewRecord(it) })) }
            envelope.notificationSettings?.let { put("notificationSettings", notificationRecord(it)) }
            envelope.archiveSettings?.let {
                put("archiveSettings", buildJsonObject {
                    put("id", JsonPrimitive("settings"))
                    put("enabled", JsonPrimitive(it.enabled))
                    put("archiveAfterDays", JsonPrimitive(it.archiveAfterDays))
                })
            }
            envelope.appPreferences?.let {
                put("appPreferences", buildJsonObject {
                    put("id", JsonPrimitive("preferences"))
                    put("pinnedSmartViewIds", JsonArray(it.pinnedSmartViewIds.map(::JsonPrimitive)))
                    put("maxPinnedViews", JsonPrimitive(5)) // web-schema-required constants
                    put("smartViewsEnabled", JsonPrimitive(true)) // (P§2.1; iOS exports them too)
                })
            }
        }
        return pretty.encodeToString(JsonElement.serializer(), sortKeys(root))
    }

    /** A domain task record plus the backup-only derived quadrant (import ignores it). */
    private fun taskRecord(task: Task, vararg extras: Pair<String, String>): JsonObject {
        val base = TaskJson.json.parseToJsonElement(TaskJson.encode(task)).jsonObject
        return JsonObject(base + ("quadrant" to JsonPrimitive(task.quadrant.id)) +
            extras.associate { (k, v) -> k to JsonPrimitive(v) })
    }

    private fun smartViewRecord(view: SmartView): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(view.id))
        put("name", JsonPrimitive(view.name))
        put("icon", JsonPrimitive(view.icon))
        put("criteria", TaskJson.json.encodeToJsonElement(FilterCriteria.serializer(), view.criteria))
        put("isBuiltIn", JsonPrimitive(view.isBuiltIn))
        put("createdAt", JsonPrimitive(iso(view.createdAt)))
        put("updatedAt", JsonPrimitive(iso(view.updatedAt)))
    }

    private fun notificationRecord(s: BackupNotificationSettings): JsonObject = buildJsonObject {
        put("id", JsonPrimitive("settings"))
        put("enabled", JsonPrimitive(s.enabled))
        put("defaultReminder", JsonPrimitive(s.defaultReminder))
        put("soundEnabled", JsonPrimitive(s.soundEnabled))
        s.quietHoursStart?.let { put("quietHoursStart", JsonPrimitive(it)) } // omit when off
        s.quietHoursEnd?.let { put("quietHoursEnd", JsonPrimitive(it)) }
        put("permissionAsked", JsonPrimitive(s.permissionAsked))
        put("updatedAt", JsonPrimitive(iso(s.updatedAt)))
    }

    fun parse(raw: String, limitBytes: Int = 10 * 1024 * 1024, limitTasks: Int = 10_000): ImportResult {
        if (raw.toByteArray(Charsets.UTF_8).size > limitBytes) throw TooLarge("Import exceeds size limit")
        val root = Json.parseToJsonElement(raw).jsonObject
        val entries = root["tasks"]?.jsonArray ?: JsonArray(emptyList())
        if (entries.size > limitTasks) throw TooLarge("Import exceeds $limitTasks tasks")
        var skipped = 0
        val tasks = entries.mapNotNull { element ->
            TaskJson.decodeOrNull(element) ?: run {
                skipped++
                null
            }
        }
        return ImportResult(tasks, skipped)
    }

    private fun iso(instant: Instant): String =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .format(instant.atZone(java.time.ZoneOffset.UTC))

    private fun sortKeys(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to sortKeys(it.value) })
        is JsonArray -> JsonArray(element.map(::sortKeys))
        else -> element
    }
}

/**
 * Merge-mode import (spec §8.16): colliding IDs regenerate; `dependencies`
 * and `parentTaskId` remap through the complete ID map — forward references
 * included, which is why the map is built before any rewriting.
 */
object MergePlan {
    fun compute(existing: List<Task>, imported: List<Task>): List<Task> {
        val taken = existing.map { it.id }.toMutableSet()
        val idMap = mutableMapOf<String, String>()
        imported.forEach { task ->
            val newId = if (task.id in taken) {
                var candidate = NanoId.generate()
                while (candidate in taken || imported.any { it.id == candidate }) {
                    candidate = NanoId.generate()
                }
                candidate
            } else {
                task.id
            }
            idMap[task.id] = newId
            taken += newId
        }
        return imported.map { task ->
            task.copy(
                id = idMap.getValue(task.id),
                dependencies = task.dependencies.map { idMap[it] ?: it },
                parentTaskId = task.parentTaskId?.let { idMap[it] ?: it },
            )
        }
    }
}
