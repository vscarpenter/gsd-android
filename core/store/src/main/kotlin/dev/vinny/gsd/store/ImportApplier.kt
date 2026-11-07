package dev.vinny.gsd.store

import androidx.room.withTransaction
import dev.vinny.gsd.model.BackupAppPreferences
import dev.vinny.gsd.model.BackupArchiveSettings
import dev.vinny.gsd.model.BackupImport
import dev.vinny.gsd.model.BackupNotificationSettings
import dev.vinny.gsd.model.MergePlan
import dev.vinny.gsd.model.SmartViewLimits
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.model.TaskValidator
import java.time.Clock
import java.time.Instant

enum class ImportMode { MERGE, REPLACE }

data class ImportSummary(val imported: Int, val skipped: Int)

/** SQLite's default bound-variable limit is 999; chunk `IN (...)` deletes well under it. */
private const val SQL_IN_CHUNK_SIZE = 500

/**
 * Applies a parsed backup import across all seven stores (P§2.2). Task
 * import reuses TaskStore's existing replace/merge entry points, lifted
 * unchanged from SettingsScreen's inline import handler. The six non-task
 * stores skip on collision in merge mode (the existing row wins, ids are
 * never remapped) and the three configuration singletons apply in replace
 * mode only — merge never adopts another device's configuration (P§2.2).
 * None of the six non-task stores touch the sync queue.
 */
class ImportApplier(
    private val db: GsdDatabase,
    private val taskStore: TaskStore,
    private val clock: Clock,
) {
    /**
     * One transaction for the whole import (review finding 39), so a failure part way leaves the
     * stores as they were instead of half imported. A task over a local limit is skipped and counted
     * like a corrupt row (P§2.2): one iOS title with an emoji must not abort the batch.
     */
    suspend fun apply(import: BackupImport, mode: ImportMode): ImportSummary = db.withTransaction {
        val env = import.envelope
        val now = clock.instant()
        val (tasks, overLimit) = env.tasks.partition { it.passesValidation(now) }
        var skipped = import.skipped + overLimit.size
        // Configuration first, so a task with no offset resolves to the default this import brings (D-10)
        if (mode == ImportMode.REPLACE) { // merge never adopts another device's configuration (P§2.2)
            env.notificationSettings?.let { db.notificationSettingsDao().upsert(it.toEntity()) }
            env.archiveSettings?.let { db.archiveSettingsDao().upsert(it.toEntity()) }
            env.appPreferences?.let { db.appPreferencesDao().upsert(it.toEntity()) }
        }
        val imported = when (mode) {
            ImportMode.REPLACE -> {
                taskStore.replaceAll(tasks)
                tasks.size
            }
            ImportMode.MERGE -> {
                val existing = db.taskDao().all().map { it.toTask() }
                val merged = MergePlan.compute(existing, tasks)
                merged.forEach { taskStore.add(it) }
                merged.size
            }
        }
        val activeIds = db.taskDao().all().map { it.id }.toSet()

        // An id that just became active must not remain a tombstone anywhere else — the
        // one-store-per-id invariant (P§3) runs both directions, so this sweeps local
        // archive/trash rows regardless of what the envelope itself carries.
        activeIds.chunked(SQL_IN_CHUNK_SIZE).forEach { chunk ->
            db.archivedTaskDao().deleteByIds(chunk)
            db.deletedTaskDao().deleteByIds(chunk)
        }

        env.archivedTasks?.let { rows ->
            if (mode == ImportMode.REPLACE) db.archivedTaskDao().clear()
            val keep = db.archivedTaskDao().allIds().toSet()
            rows.filter { it.task.id !in activeIds && it.task.id !in keep }
                .forEach { db.archivedTaskDao().upsert(it.task.toEntity().toArchived(it.archivedAt.toEpochMilli())) }
        }

        env.deletedTasks?.let { rows ->
            if (mode == ImportMode.REPLACE) db.deletedTaskDao().clear()
            val archived = db.archivedTaskDao().allIds().toSet()
            val keep = db.deletedTaskDao().allIds().toSet()
            rows.filter { it.task.id !in activeIds && it.task.id !in archived && it.task.id !in keep }
                .forEach { db.deletedTaskDao().upsert(it.task.toEntity().toDeleted(it.deletedAt.toEpochMilli())) }
        }

        env.smartViews?.let { views ->
            if (mode == ImportMode.REPLACE) db.smartViewDao().clearCustom()
            val existingIds = db.smartViewDao().customIds().toMutableSet()
            var admitted = 0
            views.filter { !it.isBuiltIn }.forEach { view ->
                val isNew = view.id !in existingIds
                if (isNew && admitted >= SmartViewLimits.MAX_SMART_VIEWS) { skipped++; return@forEach }
                db.smartViewDao().upsert(view.toEntity())
                if (isNew) { existingIds += view.id; admitted++ }
            }
        }

        ImportSummary(imported, skipped)
    }
}

private fun Task.passesValidation(now: Instant): Boolean =
    try {
        TaskValidator.validate(this, now)
        true
    } catch (_: TaskValidationError) {
        false
    }

private fun BackupNotificationSettings.toEntity(): NotificationSettingsEntity = NotificationSettingsEntity(
    enabled = enabled,
    defaultReminder = defaultReminder,
    soundEnabled = soundEnabled,
    quietHoursStart = quietHoursStart,
    quietHoursEnd = quietHoursEnd,
    permissionAsked = permissionAsked,
    updatedAt = updatedAt.toEpochMilli(),
)

private fun BackupArchiveSettings.toEntity(): ArchiveSettingsEntity =
    ArchiveSettingsEntity(enabled = enabled, archiveAfterDays = archiveAfterDays)

private fun BackupAppPreferences.toEntity(): AppPreferencesEntity =
    AppPreferencesEntity(pinnedSmartViewIdsJson = TaskJson.encodeIds(pinnedSmartViewIds))
