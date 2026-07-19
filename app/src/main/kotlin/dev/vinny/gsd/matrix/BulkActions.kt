package dev.vinny.gsd.matrix

import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.TaskValidator
import dev.vinny.gsd.store.TaskStore
import java.time.Instant

sealed interface BulkOp {
    data object Complete : BulkOp
    data class MoveTo(val quadrant: Quadrant) : BulkOp
    data class AddTag(val tag: String) : BulkOp
    data class RemoveTag(val tag: String) : BulkOp
    data class SetDue(val due: Instant?) : BulkOp
    data object Delete : BulkOp
}

data class BulkResult(val succeeded: Int, val failed: Int)

/**
 * Bulk operations (spec §8.11): per-task validation through the store, partial
 * failures counted and reported — one bad task never aborts the batch.
 */
object BulkActions {
    suspend fun apply(store: TaskStore, ids: Set<String>, op: BulkOp): BulkResult {
        var succeeded = 0
        var failed = 0
        ids.forEach { id ->
            try {
                val applied = when (op) {
                    BulkOp.Complete -> store.setCompleted(id, true) != null
                    is BulkOp.MoveTo -> store.get(id)?.let {
                        store.update(it.copy(urgent = op.quadrant.urgent, important = op.quadrant.important))
                        true
                    } ?: false
                    is BulkOp.AddTag -> store.get(id)?.let {
                        store.update(it.copy(tags = TaskValidator.normalizeTags(it.tags + op.tag)))
                        true
                    } ?: false
                    is BulkOp.RemoveTag -> store.get(id)?.let {
                        store.update(it.copy(tags = it.tags - op.tag))
                        true
                    } ?: false
                    is BulkOp.SetDue -> store.get(id)?.let {
                        store.update(it.copy(dueDate = op.due))
                        true
                    } ?: false
                    BulkOp.Delete -> store.delete(id) != null
                }
                if (applied) succeeded++ else failed++
            } catch (_: Exception) {
                failed++
            }
        }
        return BulkResult(succeeded, failed)
    }
}
