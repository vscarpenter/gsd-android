package dev.vinny.gsd.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
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
    abstract fun archivedTaskDao(): ArchivedTaskDao
    abstract fun archiveSettingsDao(): ArchiveSettingsDao
    abstract fun smartViewDao(): SmartViewDao
    abstract fun appPreferencesDao(): AppPreferencesDao
    abstract fun notificationSettingsDao(): NotificationSettingsDao

    companion object {
        /** Keeps Room a private concern of this module — callers never see the builder. */
        fun build(context: Context): GsdDatabase =
            Room.databaseBuilder(context, GsdDatabase::class.java, "gsd.db").build()

        /** Hermetic store for tests. */
        fun buildInMemory(context: Context): GsdDatabase =
            Room.inMemoryDatabaseBuilder(context, GsdDatabase::class.java).build()
    }
}
