package dev.vinny.gsd.store

import androidx.room.withTransaction

/**
 * Erase-all-data, local part (spec §8.16): clears tasks, archive, custom
 * views, sync queue, pins, archive settings. Theme and onboarding flags live
 * in DataStore and are deliberately untouched. The remote wipe is Phase 5.
 */
object LocalErase {
    suspend fun eraseAll(db: GsdDatabase) {
        db.withTransaction {
            db.taskDao().clear()
            db.archivedTaskDao().clear()
            db.syncQueueDao().clear()
            db.smartViewDao().clearCustom()
            db.appPreferencesDao().clear()
            db.archiveSettingsDao().clear()
        }
    }
}
