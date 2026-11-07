package dev.vinny.gsd.sync

import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.FocusFields
import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.SavedPlacement
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeEntry
import dev.vinny.gsd.model.TopThree
import java.time.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** Local ⇄ wire translation (spec §9.2). The wire time-entry form is lossy on purpose. */
object WireMapper {

    /**
     * [hasCurrentRow] false leaves `saved_at` and the top three pick off the record, so the server keeps its values.
     * Push uses that when it can't read the task's current row, since a payload queued by an older build has neither
     * (C4-6).
     * [defaultReminder] is this device's default: the wire always carries a number (D-10).
     */
    fun push(
        task: Task,
        deviceId: String,
        owner: String,
        defaultReminder: Int,
        hasCurrentRow: Boolean = true,
    ): WireTask = WireTask(
        id = "",
        taskId = task.id,
        owner = owner, // required by the collection rule — blank is a 400
        title = task.title,
        description = task.description,
        urgent = task.urgent,
        important = task.important,
        quadrant = task.quadrant.id,
        dueDate = PbDates.formatWire(task.dueDate),
        completedAt = PbDates.formatWire(task.completedAt),
        lastNotificationAt = PbDates.formatWire(task.lastNotificationAt),
        snoozedUntil = PbDates.formatWire(task.snoozedUntil),
        savedAt = if (hasCurrentRow) PbDates.formatWire(task.savedAt) else null,
        focusDate = wireFocus(task, hasCurrentRow)?.focusDate,
        focusRank = wireFocus(task, hasCurrentRow)?.let { JsonPrimitive(it.focusRank) },
        completed = task.completed,
        recurrence = task.recurrence.wire,
        tags = task.tags,
        subtasks = task.subtasks.map { WireSubtask(it.id, it.title, it.completed) },
        dependencies = task.dependencies,
        notificationEnabled = task.notificationEnabled,
        notificationSent = task.notificationSent,
        notifyBefore = task.notifyBefore ?: defaultReminder, // D-10: the server stores a null number as 0
        estimatedMinutes = task.estimatedMinutes,
        timeSpent = task.timeSpent,
        timeEntries = task.timeEntries.map { entry ->
            WireTimeEntry(
                id = entry.id,
                startedAt = PbDates.formatWire(entry.startedAt),
                minutes = entry.endedAt
                    ?.let { ((it.toEpochMilli() - entry.startedAt.toEpochMilli()) / 60_000L).toInt().coerceAtLeast(0) }
                    ?: 0,
            )
        },
        clientUpdatedAt = PbDates.formatWire(task.updatedAt),
        clientCreatedAt = PbDates.formatWire(task.createdAt),
        deviceId = deviceId,
    )

    /** The pick as the row holds it, `""` and `0` when unset, or null to leave both keys off (C4-6). */
    private fun wireFocus(task: Task, hasCurrentRow: Boolean): TopThree.WireFocus? =
        TopThree.pushFocus(FocusFields(task.focusDate, task.focusRank).takeIf { hasCurrentRow })

    /**
     * Wire → local. Null when the record is unusable (missing task_id was
     * already rejected at decode; unparseable client_updated_at skips here).
     * With a local copy, remote wins synced fields but device-local state and
     * the rich time entries are preserved (spec §4.7, §9.2). Reminder state is the
     * exception when the pull moves the due date (parity backlog B-55, web #669).
     */
    fun pull(wire: WireTask, local: Task?): Task? {
        if (wire.taskId.length < 4) return null
        val updatedAt = PbDates.parse(wire.clientUpdatedAt) ?: return null
        val createdAt = PbDates.parse(wire.clientCreatedAt) ?: updatedAt
        val reconstructed = wire.timeEntries.map { entry ->
            val started = PbDates.parse(entry.startedAt) ?: Instant.EPOCH
            TimeEntry(
                id = entry.id.ifEmpty { "wire" + entry.hashCode() },
                startedAt = started,
                endedAt = started.plusSeconds(entry.minutes * 60L),
                notes = null,
            )
        }
        val dueDate = PbDates.parse(wire.dueDate)
        // A new due date is a new reminder, as a local edit makes it (EditorRules): a reminder this
        // device already showed for the old date would otherwise hide the new one. Compared as instants.
        val reminderSource = local?.takeIf { it.dueDate?.toEpochMilli() == dueDate?.toEpochMilli() }
        // Only a real date with an integer rank from 1 to 3 is a pick (spec-top-three.md §3.5)
        val pulledFocus = TopThree.fromWire(wire.focusDate, wire.focusRank?.takeIf { !it.isString }?.doubleOrNull)
        val pulled = Task(
            id = wire.taskId,
            title = wire.title,
            description = wire.description,
            urgent = wire.urgent,
            important = wire.important,
            completed = wire.completed,
            completedAt = PbDates.parse(wire.completedAt),
            createdAt = createdAt,
            updatedAt = updatedAt,
            dueDate = dueDate,
            recurrence = Recurrence.fromWire(wire.recurrence),
            tags = wire.tags,
            subtasks = wire.subtasks.map { Subtask(it.id, it.title, it.completed) },
            dependencies = wire.dependencies,
            parentTaskId = local?.parentTaskId,
            notifyBefore = wire.notifyBefore,
            notificationEnabled = wire.notificationEnabled,
            notificationSent = reminderSource?.notificationSent ?: false,
            lastNotificationAt = reminderSource?.lastNotificationAt,
            snoozedUntil = reminderSource?.snoozedUntil,
            estimatedMinutes = wire.estimatedMinutes?.takeIf { it != 0 },
            timeEntries = local?.timeEntries ?: reconstructed,
            savedAt = PbDates.parse(wire.savedAt),
            focusDate = pulledFocus.focusDate,
            focusRank = pulledFocus.focusRank,
        )
        return SavedPlacement.afterRemoteChange(local, pulled)
    }
}
