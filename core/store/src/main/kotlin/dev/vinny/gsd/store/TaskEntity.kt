package dev.vinny.gsd.store

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.model.TimeEntry
import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer

/**
 * Collections live in JSON TEXT columns, matching the Dexie/PocketBase/GRDB
 * shapes (spec §7.10). `quadrant` is persisted only as an indexed column and
 * always recomputed from the flags on read.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("quadrant"), Index("completed"), Index("updatedAt"), Index("dueDate")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val urgent: Boolean,
    val important: Boolean,
    val quadrant: String,
    val completed: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val dueDate: Long?,
    val recurrence: String,
    val tagsJson: String,
    val subtasksJson: String,
    val dependenciesJson: String,
    val parentTaskId: String?,
    val notifyBefore: Int?,
    val notificationEnabled: Boolean,
    val notificationSent: Boolean,
    val lastNotificationAt: Long?,
    val snoozedUntil: Long?,
    val estimatedMinutes: Int?,
    val timeEntriesJson: String,
    val savedAt: Long? = null,
)

private val subtaskListSerializer = ListSerializer(Subtask.serializer())
private val timeEntryListSerializer = ListSerializer(TimeEntry.serializer())

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    urgent = urgent,
    important = important,
    quadrant = quadrant.id,
    completed = completed,
    completedAt = completedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    dueDate = dueDate?.toEpochMilli(),
    recurrence = recurrence.wire,
    tagsJson = TaskJson.encodeIds(tags),
    subtasksJson = TaskJson.json.encodeToString(subtaskListSerializer, subtasks),
    dependenciesJson = TaskJson.encodeIds(dependencies),
    parentTaskId = parentTaskId,
    notifyBefore = notifyBefore,
    notificationEnabled = notificationEnabled,
    notificationSent = notificationSent,
    lastNotificationAt = lastNotificationAt?.toEpochMilli(),
    snoozedUntil = snoozedUntil?.toEpochMilli(),
    estimatedMinutes = estimatedMinutes,
    timeEntriesJson = TaskJson.json.encodeToString(timeEntryListSerializer, timeEntries),
    savedAt = savedAt?.toEpochMilli(),
)

fun TaskEntity.toTask(): Task = Task(
    id = id,
    title = title,
    description = description,
    urgent = urgent,
    important = important,
    completed = completed,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    dueDate = dueDate?.let(Instant::ofEpochMilli),
    recurrence = Recurrence.fromWire(recurrence),
    tags = TaskJson.decodeIds(tagsJson),
    subtasks = decodeOrEmpty(subtasksJson, subtaskListSerializer),
    dependencies = TaskJson.decodeIds(dependenciesJson),
    parentTaskId = parentTaskId,
    notifyBefore = notifyBefore,
    notificationEnabled = notificationEnabled,
    notificationSent = notificationSent,
    lastNotificationAt = lastNotificationAt?.let(Instant::ofEpochMilli),
    snoozedUntil = snoozedUntil?.let(Instant::ofEpochMilli),
    estimatedMinutes = estimatedMinutes,
    timeEntries = decodeOrEmpty(timeEntriesJson, timeEntryListSerializer),
    savedAt = savedAt?.let(Instant::ofEpochMilli),
)

private fun <T> decodeOrEmpty(
    raw: String,
    serializer: kotlinx.serialization.KSerializer<List<T>>,
): List<T> =
    try {
        TaskJson.json.decodeFromString(serializer, raw)
    } catch (_: Exception) {
        emptyList()
    }
