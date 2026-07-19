package dev.vinny.gsd.store

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.vinny.gsd.model.ReminderSettings
import java.time.Clock
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
interface NotificationSettingsDao {
    @Query("SELECT * FROM notification_settings WHERE id = 1")
    fun observe(): Flow<NotificationSettingsEntity?>

    @Query("SELECT * FROM notification_settings WHERE id = 1")
    suspend fun get(): NotificationSettingsEntity?

    @Upsert
    suspend fun upsert(entity: NotificationSettingsEntity)
}

/** The §7.4 singleton, synthesized with spec defaults until the first write. */
class NotificationSettingsStore(private val db: GsdDatabase, private val clock: Clock) {

    fun observe(): Flow<ReminderSettings> =
        db.notificationSettingsDao().observe().map { it?.toSettings() ?: ReminderSettings() }

    suspend fun get(): ReminderSettings =
        db.notificationSettingsDao().get()?.toSettings() ?: ReminderSettings()

    suspend fun update(transform: (ReminderSettings) -> ReminderSettings) {
        val updated = transform(get())
        db.notificationSettingsDao().upsert(updated.toEntity(clock.millis()))
    }

    suspend fun markPermissionAsked() = update { it.copy(permissionAsked = true) }
}

private fun NotificationSettingsEntity.toSettings(): ReminderSettings = ReminderSettings(
    enabled = enabled,
    defaultReminderMinutes = defaultReminder,
    soundEnabled = soundEnabled,
    quietHoursStart = quietHoursStart?.let(LocalTime::parse),
    quietHoursEnd = quietHoursEnd?.let(LocalTime::parse),
    permissionAsked = permissionAsked,
)

private fun ReminderSettings.toEntity(updatedAt: Long): NotificationSettingsEntity =
    NotificationSettingsEntity(
        enabled = enabled,
        defaultReminder = defaultReminderMinutes,
        soundEnabled = soundEnabled,
        quietHoursStart = quietHoursStart?.toString(),
        quietHoursEnd = quietHoursEnd?.toString(),
        permissionAsked = permissionAsked,
        updatedAt = updatedAt,
    )
