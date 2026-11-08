package dev.vinny.gsd.store

import androidx.room.withTransaction

/**
 * Erase-all-data, local part (spec §8.16): clears tasks, archive, trash,
 * custom views, sync queue, pins, archive settings, the pull cursor, and the remembered deletes. Theme and onboarding
 * flags live in DataStore and are deliberately untouched. The remote wipe is
 * Phase 5. Trash inclusion matches iOS's "erase must mean all of it"
 * (parity spec P§3.1) — the web's omission there is a known upstream bug,
 * not behavior to copy.
 */
object LocalErase {
    suspend fun eraseAll(db: GsdDatabase) {
        db.withTransaction {
            db.taskDao().clear()
            db.archivedTaskDao().clear()
            db.deletedTaskDao().clear()
            db.syncQueueDao().clear()
            db.smartViewDao().clearCustom()
            db.appPreferencesDao().clear()
            db.archiveSettingsDao().clear()
            // The pull cursor means "everything up to here is on this device", so it goes with the data, and so do
            // the remembered deletes, which name tasks this device held (SPEC-3 rule 2). The device identity stays:
            // other devices use it to drop this device's echoes.
            db.syncMetadataDao().get()?.let {
                db.syncMetadataDao().upsert(it.copy(cursor = null, resurrectTaskIdsJson = "[]"))
            }
        }
    }
}
