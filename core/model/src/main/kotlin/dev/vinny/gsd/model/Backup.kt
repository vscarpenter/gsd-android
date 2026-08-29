package dev.vinny.gsd.model

import java.time.Instant

data class ArchivedTask(val task: Task, val archivedAt: Instant)
data class DeletedTask(val task: Task, val deletedAt: Instant)

data class BackupNotificationSettings(
    val enabled: Boolean,
    val defaultReminder: Int,
    val soundEnabled: Boolean,
    val quietHoursStart: String?, // "HH:mm" or null = omit
    val quietHoursEnd: String?, // "HH:mm" or null = omit
    val permissionAsked: Boolean,
    val updatedAt: Instant,
)

data class BackupArchiveSettings(val enabled: Boolean, val archiveAfterDays: Int)

data class BackupAppPreferences(val pinnedSmartViewIds: List<String>)

/**
 * The shared 2.1.0 backup envelope (P§2.1). A `null` list/singleton means the
 * key is absent from the document; an empty list means the key is present and
 * empty — the two are not interchangeable (P§2.2).
 */
data class BackupEnvelope(
    val tasks: List<Task>,
    val archivedTasks: List<ArchivedTask>? = null,
    val deletedTasks: List<DeletedTask>? = null,
    val smartViews: List<SmartView>? = null,
    val notificationSettings: BackupNotificationSettings? = null,
    val archiveSettings: BackupArchiveSettings? = null,
    val appPreferences: BackupAppPreferences? = null,
)
