package dev.vinny.gsd.store

import dev.vinny.gsd.model.BackupAppPreferences
import dev.vinny.gsd.model.BackupArchiveSettings
import dev.vinny.gsd.model.BackupImport
import dev.vinny.gsd.model.BackupNotificationSettings
import dev.vinny.gsd.model.MergePlan
import dev.vinny.gsd.model.TaskJson
import java.time.Clock
import kotlinx.coroutines.flow.first

enum class ImportMode { MERGE, REPLACE }

data class ImportSummary(val imported: Int, val skipped: Int)

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
    suspend fun apply(import: BackupImport, mode: ImportMode): ImportSummary {
        val env = import.envelope
        val imported = when (mode) {
            ImportMode.REPLACE -> {
                taskStore.replaceAll(env.tasks)
                env.tasks.size
            }
            ImportMode.MERGE -> {
                val existing = taskStore.observeTasks().first()
                val merged = MergePlan.compute(existing, env.tasks)
                merged.forEach { taskStore.add(it) }
                merged.size
            }
        }
        val activeIds = taskStore.observeTasks().first().map { it.id }.toSet()

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
            views.filter { !it.isBuiltIn }.forEach { db.smartViewDao().upsert(it.toEntity()) }
        }

        if (mode == ImportMode.REPLACE) { // merge never adopts another device's configuration (P§2.2)
            env.notificationSettings?.let { db.notificationSettingsDao().upsert(it.toEntity()) }
            env.archiveSettings?.let { db.archiveSettingsDao().upsert(it.toEntity()) }
            env.appPreferences?.let { db.appPreferencesDao().upsert(it.toEntity()) }
        }

        return ImportSummary(imported, import.skipped)
    }
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
