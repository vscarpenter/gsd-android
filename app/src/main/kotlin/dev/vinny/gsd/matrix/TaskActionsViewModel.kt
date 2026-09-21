package dev.vinny.gsd.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.store.TrashStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Undo-snackbar payload (parity spec P§3.1): just enough to relabel and restore, not re-create. */
data class DeletedSnapshot(val id: String, val title: String)

/**
 * The operations behind a task row's menu, swipes, and disc (spec §8.1). One view model owns them,
 * so every screen that draws a row offers the same actions. A screen creates one beside its own
 * view model, the way it creates the editor's.
 */
class TaskActionsViewModel(
    private val store: TaskStore,
    private val trashStore: TrashStore,
) : ViewModel() {
    fun toggleCompleted(id: String) {
        viewModelScope.launch {
            val current = store.get(id) ?: return@launch
            store.setCompleted(id, !current.completed)
        }
    }

    private val _lastDeleted = MutableStateFlow<DeletedSnapshot?>(null)
    val lastDeleted: StateFlow<DeletedSnapshot?> = _lastDeleted.asStateFlow()

    /** Delete commits at once (spec §8.20). The snackbar offers undo after the fact. */
    fun delete(id: String) {
        viewModelScope.launch {
            _lastDeleted.value = store.delete(id)?.let { DeletedSnapshot(it.id, it.title) }
        }
    }

    /** Undo restores from trash (parity spec P§3.1). The original timestamps come back with it. */
    fun undoDelete() {
        val snapshot = _lastDeleted.value ?: return
        viewModelScope.launch {
            trashStore.restore(snapshot.id)
            _lastDeleted.value = null
        }
    }

    fun clearUndo() {
        _lastDeleted.value = null
    }

    /** Drag-and-drop reclassification (spec §6.2): the flags change, quadrant follows. */
    fun moveToQuadrant(id: String, quadrant: Quadrant) {
        viewModelScope.launch {
            val current = store.get(id) ?: return@launch
            if (current.quadrant == quadrant) return@launch
            store.change(id) { it.copy(urgent = quadrant.urgent, important = quadrant.important) }
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch { store.duplicate(id) }
    }

    fun snooze(id: String, duration: java.time.Duration) {
        viewModelScope.launch { store.snooze(id, java.time.Instant.now().plus(duration)) }
    }

    fun toggleTimer(id: String) {
        viewModelScope.launch {
            val current = store.get(id) ?: return@launch
            if (TimeTracking.runningEntry(current) != null) store.stopTimer(id) else store.startTimer(id)
        }
    }
}
