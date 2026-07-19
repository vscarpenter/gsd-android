package dev.vinny.gsd.sync

import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.SyncHistoryEntity
import dev.vinny.gsd.store.SyncMetadataEntity
import dev.vinny.gsd.store.SyncQueueEntity
import dev.vinny.gsd.store.toEntity
import dev.vinny.gsd.store.toTask
import java.util.UUID

/**
 * SyncLocalStore over Room. Remote-sourced writes go straight to the DAOs —
 * deliberately outside the TaskStore mutation path so nothing re-stamps
 * updatedAt or enqueues sync for a pulled record.
 */
class RoomSyncLocalStore(private val db: GsdDatabase) : SyncLocalStore {

    override suspend fun activeTasks(): List<Task> = db.taskDao().all().map { it.toTask() }

    override suspend fun taskById(id: String): Task? = db.taskDao().byId(id)?.toTask()

    override suspend fun upsertFromRemote(task: Task) = db.taskDao().upsert(task.toEntity())

    override suspend fun deleteFromRemote(id: String) = db.taskDao().deleteById(id)

    override suspend fun pendingQueue(): List<SyncQueueEntity> = db.syncQueueDao().pending()

    override suspend fun failedQueue(): List<SyncQueueEntity> = db.syncQueueDao().failed()

    override suspend fun markQueueItemDone(id: Long) = db.syncQueueDao().deleteById(id)

    override suspend fun bumpQueueRetry(id: Long, error: String, attemptAtMs: Long, nowFailed: Boolean) {
        val current = db.syncQueueDao().pending().firstOrNull { it.id == id } ?: return
        db.syncQueueDao().bumpRetry(
            id = id,
            retryCount = current.retryCount + 1,
            error = error,
            attemptAt = attemptAtMs,
            status = if (nowFailed) SyncQueueEntity.STATUS_FAILED else SyncQueueEntity.STATUS_PENDING,
            failedAt = if (nowFailed) attemptAtMs else null,
        )
    }

    override suspend fun requeueFailed() = db.syncQueueDao().requeueFailed()

    override suspend fun cursor(): String? = db.syncMetadataDao().get()?.cursor

    override suspend fun saveCursor(cursor: String) {
        db.syncMetadataDao().upsert(metadata().copy(cursor = cursor))
    }

    override suspend fun deviceId(): String = metadata().deviceId

    /** Stable device identity, created on first use (spec §9.8). */
    private suspend fun metadata(): SyncMetadataEntity =
        db.syncMetadataDao().get() ?: SyncMetadataEntity(
            cursor = null,
            deviceId = UUID.randomUUID().toString(),
            deviceName = android.os.Build.MODEL ?: "Android",
        ).also { db.syncMetadataDao().upsert(it) }

    override suspend fun insertHistory(entry: SyncHistoryEntry) {
        db.syncHistoryDao().insert(
            SyncHistoryEntity(
                timestamp = entry.timestamp,
                status = entry.status,
                pushedCount = entry.pushedCount,
                pulledCount = entry.pulledCount,
                conflictsResolved = entry.conflictsResolved,
                failedCount = entry.failedCount,
                errorMessage = entry.errorMessage,
                duration = entry.durationMs,
                deviceId = entry.deviceId,
                triggeredBy = entry.triggeredBy,
            ),
        )
        db.syncHistoryDao().prune()
    }
}
