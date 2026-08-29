package dev.vinny.gsd.store

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tables declared in schema v1 but not yet driven by any feature (spec §7.10).
 * Later phases add rows and DAOs, not migrations. Shapes follow the spec:
 * archive §8.12, smart views §7.6, notification settings §7.4, archive
 * settings §7.5, app preferences §7.7, sync metadata/history §9.7–§9.8.
 */

@Entity(
    tableName = "archived_tasks",
    indices = [Index("archivedAt"), Index("completedAt")],
)
data class ArchivedTaskEntity(
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
    val archivedAt: Long,
)

@Entity(
    tableName = "deleted_tasks",
    indices = [Index("deletedAt")],
)
data class DeletedTaskEntity(
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
    val deletedAt: Long,
)

@Entity(tableName = "smart_views")
data class SmartViewEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val criteriaJson: String,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "notification_settings")
data class NotificationSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = true,
    val defaultReminder: Int = 15,
    val soundEnabled: Boolean = true,
    val quietHoursStart: String? = null,
    val quietHoursEnd: String? = null,
    val permissionAsked: Boolean = false,
    val updatedAt: Long,
)

@Entity(tableName = "archive_settings")
data class ArchiveSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean = false,
    val archiveAfterDays: Int = 30,
)

@Entity(tableName = "app_preferences")
data class AppPreferencesEntity(
    @PrimaryKey val id: Int = 1,
    val pinnedSmartViewIdsJson: String = "[]",
)

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey val id: Int = 1,
    val cursor: String? = null,
    val deviceId: String,
    val deviceName: String,
)

@Entity(tableName = "sync_history", indices = [Index("timestamp")])
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val status: String,
    val pushedCount: Int,
    val pulledCount: Int,
    val conflictsResolved: Int,
    val failedCount: Int?,
    val errorMessage: String?,
    val duration: Long?,
    val deviceId: String,
    val triggeredBy: String,
)
