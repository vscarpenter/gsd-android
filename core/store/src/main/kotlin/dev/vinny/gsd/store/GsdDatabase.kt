package dev.vinny.gsd.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class SyncOpConverter {
    @TypeConverter
    fun fromSyncOp(op: SyncOp): String = op.name

    @TypeConverter
    fun toSyncOp(raw: String): SyncOp = SyncOp.valueOf(raw)
}

@Database(
    entities = [
        TaskEntity::class,
        ArchivedTaskEntity::class,
        SmartViewEntity::class,
        NotificationSettingsEntity::class,
        ArchiveSettingsEntity::class,
        AppPreferencesEntity::class,
        SyncQueueEntity::class,
        SyncMetadataEntity::class,
        SyncHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(SyncOpConverter::class)
abstract class GsdDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun syncQueueDao(): SyncQueueDao
}
