package dev.vinny.gsd.model

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

data class ImportResult(val tasks: List<Task>, val skipped: Int)

/**
 * The shared interchange format (spec §8.16): envelope `{tasks, exportedAt,
 * version: 1}`, pretty-printed with sorted keys; lenient §7.3 import with
 * 10,000-task / 10 MB caps.
 */
object Interchange {
    class TooLarge(message: String) : Exception(message)

    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun export(tasks: List<Task>, exportedAt: Instant): String {
        val envelope = buildJsonObject {
            put("tasks", JsonArray(tasks.map { task ->
                TaskJson.json.parseToJsonElement(TaskJson.encode(task))
            }))
            put("exportedAt", JsonPrimitive(iso(exportedAt)))
            put("version", JsonPrimitive(1))
        }
        return pretty.encodeToString(JsonElement.serializer(), sortKeys(envelope))
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
