package dev.vinny.gsd.model

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class BackupImport(val envelope: BackupEnvelope, val version: String, val skipped: Int)

/**
 * The shared interchange format (P§2.1/§2.2, replacing spec §8.16): a
 * seven-store, versioned envelope, pretty-printed with sorted keys; lenient
 * import with 10,000-task / 10 MB caps.
 */
object Interchange {
    class TooLarge(message: String) : Exception(message)

    /** 2.2.0 adds the top three pick, `focusDate` and `focusRank`, omitted when unset (spec-top-three.md §7). */
    const val ENVELOPE_VERSION = "2.2.0"

    private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun export(envelope: BackupEnvelope, exportedAt: Instant): String {
        val root = buildJsonObject {
            put("version", JsonPrimitive(ENVELOPE_VERSION))
            put("exportedAt", JsonPrimitive(iso(exportedAt)))
            put("tasks", JsonArray(envelope.tasks.map { taskRecord(it) }))
            envelope.archivedTasks?.let { rows ->
                put("archivedTasks", JsonArray(rows.map { taskRecord(it.task, "archivedAt" to iso(it.archivedAt)) }))
            }
            envelope.deletedTasks?.let { rows ->
                put("deletedTasks", JsonArray(rows.map { taskRecord(it.task, "deletedAt" to iso(it.deletedAt)) }))
            }
            envelope.smartViews?.let { views ->
                val clipped = views.sortedWith(compareBy({ it.createdAt }, { it.id })).take(SmartViewLimits.MAX_SMART_VIEWS)
                put("smartViews", JsonArray(clipped.map { smartViewRecord(it) }))
            }
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

    /**
     * Lenient, version-tolerant import (P§2.2): 10 MB byte cap first, then the
     * count cap over all three task-bearing arrays, then per-record leniency —
     * a corrupt row is skipped and counted, never aborts the batch.
     */
    fun parse(raw: String, limitBytes: Int = 10 * 1024 * 1024, limitTasks: Int = 10_000): BackupImport {
        if (raw.toByteArray(Charsets.UTF_8).size > limitBytes) throw TooLarge("Import exceeds size limit")
        val root = Json.parseToJsonElement(raw).jsonObject
        val taskEntries = root["tasks"]?.jsonArray ?: JsonArray(emptyList())
        val archivedEntries = root["archivedTasks"]?.jsonArray
        val deletedEntries = root["deletedTasks"]?.jsonArray
        val total = taskEntries.size + (archivedEntries?.size ?: 0) + (deletedEntries?.size ?: 0)
        if (total > limitTasks) throw TooLarge("Import exceeds $limitTasks tasks") // before decode work

        var skipped = 0
        fun decodeTasks(entries: JsonArray): List<Task> = entries.mapNotNull {
            importedTask(it) ?: run { skipped++; null }
        }
        fun timestamp(element: JsonElement, key: String): Instant =
            (element as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now()

        val version = when (val v = root["version"]) {
            null -> "1"
            else -> v.jsonPrimitive.content
        }
        return BackupImport(
            envelope = BackupEnvelope(
                tasks = decodeTasks(taskEntries),
                archivedTasks = archivedEntries?.mapNotNull { el ->
                    importedTask(el)?.let { ArchivedTask(it, timestamp(el, "archivedAt")) }
                        ?: run { skipped++; null }
                },
                deletedTasks = deletedEntries?.mapNotNull { el ->
                    importedTask(el)?.let { DeletedTask(it, timestamp(el, "deletedAt")) }
                        ?: run { skipped++; null }
                },
                smartViews = root["smartViews"]?.jsonArray?.mapNotNull { el ->
                    smartViewOrNull(el)?.takeIf { SmartViewLimits.violation(it) == null }
                        ?: run { skipped++; null }
                },
                notificationSettings = root["notificationSettings"]?.let(::notificationOrNull),
                archiveSettings = root["archiveSettings"]?.let(::archiveSettingsOrNull),
                appPreferences = root["appPreferences"]?.let(::appPreferencesOrNull),
            ),
            version = version,
            skipped = skipped,
        )
    }

    /**
     * A bad pick must not refuse a task (spec-top-three.md §7): the pair is read apart from the rest of the
     * row, and kept only as a real date with a rank from 1 to 3 ([TopThree.fromWire]). Anything else is unset.
     */
    private fun importedTask(element: JsonElement): Task? {
        val obj = element as? JsonObject ?: return null
        val task = TaskJson.decodeOrNull(JsonObject(obj - "focusDate" - "focusRank")) ?: return null
        val focus = TopThree.fromWire(
            (obj["focusDate"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
            (obj["focusRank"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull,
        )
        return task.copy(focusDate = focus.focusDate, focusRank = focus.focusRank)
    }

    /** A malformed singleton decodes to null — treated as absent, never aborts the import. */
    private fun smartViewOrNull(element: JsonElement): SmartView? = runCatching {
        val obj = element.jsonObject
        val isBuiltIn = obj.getValue("isBuiltIn").jsonPrimitive.boolean
        val criteria = TaskJson.json.decodeFromJsonElement(FilterCriteria.serializer(), obj.getValue("criteria"))
        if (isBuiltIn) {
            null // an Android guard: built-ins are derived, never stored (P§2.2)
        } else {
            SmartView(
                id = obj.getValue("id").jsonPrimitive.content,
                name = obj.getValue("name").jsonPrimitive.content,
                icon = obj.getValue("icon").jsonPrimitive.content,
                criteria = criteria,
                isBuiltIn = false,
                createdAt = Instant.parse(obj.getValue("createdAt").jsonPrimitive.content),
                updatedAt = Instant.parse(obj.getValue("updatedAt").jsonPrimitive.content),
            )
        }
    }.getOrNull()

    private fun notificationOrNull(element: JsonElement): BackupNotificationSettings? = runCatching {
        val obj = element.jsonObject
        val start = obj["quietHoursStart"]?.jsonPrimitive?.contentOrNull
        val end = obj["quietHoursEnd"]?.jsonPrimitive?.contentOrNull
        // A time that does not parse turns quiet hours off rather than reaching the store
        val quiet = QuietHours.parse(start, end).first != null
        BackupNotificationSettings(
            enabled = obj.getValue("enabled").jsonPrimitive.boolean,
            defaultReminder = obj.getValue("defaultReminder").jsonPrimitive.int,
            soundEnabled = obj.getValue("soundEnabled").jsonPrimitive.boolean,
            quietHoursStart = start.takeIf { quiet },
            quietHoursEnd = end.takeIf { quiet },
            permissionAsked = obj.getValue("permissionAsked").jsonPrimitive.boolean,
            updatedAt = Instant.parse(obj.getValue("updatedAt").jsonPrimitive.content),
        )
    }.getOrNull()

    private fun archiveSettingsOrNull(element: JsonElement): BackupArchiveSettings? = runCatching {
        val obj = element.jsonObject
        BackupArchiveSettings(
            enabled = obj.getValue("enabled").jsonPrimitive.boolean,
            archiveAfterDays = obj.getValue("archiveAfterDays").jsonPrimitive.int,
        )
    }.getOrNull()

    private fun appPreferencesOrNull(element: JsonElement): BackupAppPreferences? = runCatching {
        val obj = element.jsonObject
        BackupAppPreferences(
            pinnedSmartViewIds = obj.getValue("pinnedSmartViewIds").jsonArray
                .map { it.jsonPrimitive.content }
                .filter { it.isNotEmpty() && it.length <= SmartViewLimits.ID_MAX }
                .take(SmartViewLimits.PINNED_MAX),
        )
    }.getOrNull()

    internal fun iso(instant: Instant): String =
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
