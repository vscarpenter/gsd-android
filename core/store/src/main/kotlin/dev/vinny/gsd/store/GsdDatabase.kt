package dev.vinny.gsd.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        DeletedTaskEntity::class,
        SmartViewEntity::class,
        NotificationSettingsEntity::class,
        ArchiveSettingsEntity::class,
        AppPreferencesEntity::class,
        SyncQueueEntity::class,
        SyncMetadataEntity::class,
        SyncHistoryEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(SyncOpConverter::class)
abstract class GsdDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun archivedTaskDao(): ArchivedTaskDao
    abstract fun archiveSettingsDao(): ArchiveSettingsDao
    abstract fun deletedTaskDao(): DeletedTaskDao
    abstract fun smartViewDao(): SmartViewDao
    abstract fun appPreferencesDao(): AppPreferencesDao
    abstract fun notificationSettingsDao(): NotificationSettingsDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun syncHistoryDao(): SyncHistoryDao

    companion object {
        /** Deletion is recoverable for 30 days (spec §3): active rows move into `deleted_tasks`. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `deleted_tasks` (
                    `id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL,
                    `urgent` INTEGER NOT NULL, `important` INTEGER NOT NULL, `quadrant` TEXT NOT NULL,
                    `completed` INTEGER NOT NULL, `completedAt` INTEGER, `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL, `dueDate` INTEGER, `recurrence` TEXT NOT NULL,
                    `tagsJson` TEXT NOT NULL, `subtasksJson` TEXT NOT NULL, `dependenciesJson` TEXT NOT NULL,
                    `parentTaskId` TEXT, `notifyBefore` INTEGER, `notificationEnabled` INTEGER NOT NULL,
                    `notificationSent` INTEGER NOT NULL, `lastNotificationAt` INTEGER, `snoozedUntil` INTEGER,
                    `estimatedMinutes` INTEGER, `timeEntriesJson` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`))""",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_tasks_deletedAt` ON `deleted_tasks` (`deletedAt`)")
            }
        }

        /**
         * Saved placement (web ADR 0018) on all three task tables, and the pull contract version the sync
         * cursor was written under (spec 2026-10-29 §2). An existing cursor reads 0, so the first sync
         * after the upgrade re-pulls once and picks up `saved_at` for rows this build already holds.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("tasks", "archived_tasks", "deleted_tasks")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `savedAt` INTEGER")
                }
                db.execSQL("ALTER TABLE `sync_metadata` ADD COLUMN `contractVersion` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Keeps Room a private concern of this module — callers never see the builder. */
        /** Sign-in remembers the deletes it dropped, so the next listing can bring them back (spec §8.21). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sync_metadata` ADD COLUMN `resurrectTaskIdsJson` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        /** Top three (spec-top-three.md §5): the pick on all three task tables, nullable, with no backfill. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("tasks", "archived_tasks", "deleted_tasks")) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `focusDate` TEXT")
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `focusRank` INTEGER")
                }
            }
        }

        fun build(context: Context): GsdDatabase =
            Room.databaseBuilder(context, GsdDatabase::class.java, "gsd.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        /** Hermetic store for tests. */
        fun buildInMemory(context: Context): GsdDatabase =
            Room.inMemoryDatabaseBuilder(context, GsdDatabase::class.java).build()
    }
}
