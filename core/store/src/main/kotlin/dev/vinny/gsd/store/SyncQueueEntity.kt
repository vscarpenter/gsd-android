package dev.vinny.gsd.store

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskJson
import java.time.Instant

enum class SyncOp { CREATE, UPDATE, DELETE }

/**
 * One row per pending mutation (spec §9.5). Inserted in the same transaction
 * as — and before — the task row it describes: the enqueue-before-write
 * invariant that makes deletion reconciliation safe (spec §9.4).
 */
@Entity(tableName = "sync_queue", indices = [Index("taskId"), Index("status")])
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val operation: SyncOp,
    val timestamp: Long,
    val retryCount: Int = 0,
    val payloadJson: String?,
    val status: String = STATUS_PENDING,
    val lastError: String? = null,
    val lastAttemptAt: Long? = null,
    val failedAt: Long? = null,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_FAILED = "failed"

        fun forTask(operation: SyncOp, task: Task, now: Instant): SyncQueueEntity =
            SyncQueueEntity(
                taskId = task.id,
                operation = operation,
                timestamp = now.toEpochMilli(),
                payloadJson = TaskJson.encode(task),
            )

        fun tombstone(taskId: String, now: Instant): SyncQueueEntity =
            SyncQueueEntity(
                taskId = taskId,
                operation = SyncOp.DELETE,
                timestamp = now.toEpochMilli(),
                payloadJson = null,
            )
    }
}
