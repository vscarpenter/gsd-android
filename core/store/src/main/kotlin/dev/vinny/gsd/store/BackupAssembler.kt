package dev.vinny.gsd.store

import dev.vinny.gsd.model.ArchivedTask
import dev.vinny.gsd.model.BackupAppPreferences
import dev.vinny.gsd.model.BackupArchiveSettings
import dev.vinny.gsd.model.BackupEnvelope
import dev.vinny.gsd.model.BackupNotificationSettings
import dev.vinny.gsd.model.DeletedTask
import dev.vinny.gsd.model.TaskJson
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * Gathers all seven backup stores into one envelope (P§2.1). A full,
 * user-initiated export always emits all seven keys — `null` only ever
 * appears on import, for a key the source document omitted.
 */
class BackupAssembler(private val db: GsdDatabase) {
    suspend fun assemble(): BackupEnvelope {
        val archivedRows = db.archivedTaskDao().observeAll().first()
        val deletedRows = db.deletedTaskDao().observeAll().first()
        val viewRows = db.smartViewDao().observeAll().first()
        val notification = db.notificationSettingsDao().get() ?: NotificationSettingsEntity(updatedAt = 0L)
        val archiveSettings = db.archiveSettingsDao().get() ?: ArchiveSettingsEntity()
        val appPreferences = db.appPreferencesDao().get() ?: AppPreferencesEntity()

        return BackupEnvelope(
            tasks = db.taskDao().all().map { it.toTask() },
            archivedTasks = archivedRows.map { ArchivedTask(it.toTask(), Instant.ofEpochMilli(it.archivedAt)) },
            deletedTasks = deletedRows.map { DeletedTask(it.toTask(), Instant.ofEpochMilli(it.deletedAt)) },
            smartViews = viewRows.filter { !it.isBuiltIn }.map { it.toSmartView() },
            notificationSettings = BackupNotificationSettings(
                enabled = notification.enabled,
                defaultReminder = notification.defaultReminder,
                soundEnabled = notification.soundEnabled,
                quietHoursStart = notification.quietHoursStart,
                quietHoursEnd = notification.quietHoursEnd,
                permissionAsked = notification.permissionAsked,
                updatedAt = Instant.ofEpochMilli(notification.updatedAt),
            ),
            archiveSettings = BackupArchiveSettings(
                enabled = archiveSettings.enabled,
                archiveAfterDays = archiveSettings.archiveAfterDays,
            ),
            appPreferences = BackupAppPreferences(
                pinnedSmartViewIds = TaskJson.decodeIds(appPreferences.pinnedSmartViewIdsJson),
            ),
        )
    }
}
