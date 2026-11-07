package dev.vinny.gsd.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.SavedPlacement
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.store.TrashStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Undo-snackbar payload (parity spec P§3.1): just enough to relabel and restore, not re-create. */
data class DeletedSnapshot(val id: String, val title: String)

/** Undo-snackbar payload for Move to Saved. */
data class SavedSnapshot(val id: String, val title: String)

/** Undo-snackbar payload for Mark urgent: the task and the quadrant it moved to. */
data class UrgentSnapshot(val id: String, val title: String, val moved: Quadrant)

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
        viewModelScope.launch { deleteNow(id) }
    }

    /** [delete] for a caller that moves on only once the write worked, as the weekly sweep does. */
    suspend fun deleteNow(id: String): Boolean {
        val deleted = store.delete(id)
        _lastDeleted.value = deleted?.let { DeletedSnapshot(it.id, it.title) }
        return deleted != null
    }

    /** Undo restores from trash (parity spec P§3.1). The original timestamps come back with it. */
    fun undoDelete() {
        val snapshot = _lastDeleted.value ?: return
        viewModelScope.launch {
            trashStore.restore(snapshot.id)
            _lastDeleted.value = null
        }
    }

    /** Ends one Undo offer. A newer delete may have replaced it, and that one stays. */
    fun clearUndo(shown: DeletedSnapshot) {
        _lastDeleted.compareAndSet(shown, null)
    }

    private val _lastSaved = MutableStateFlow<SavedSnapshot?>(null)
    val lastSaved: StateFlow<SavedSnapshot?> = _lastSaved.asStateFlow()

    /**
     * Move to Saved (web ADR 0018). The menu offers it only when [SavedPlacement.canSave] holds, so a
     * refusal here means the task changed under the menu: nothing moves and no Undo is offered.
     */
    fun moveToSaved(id: String) {
        viewModelScope.launch { moveToSavedNow(id) }
    }

    /** [moveToSaved] for a caller that moves on only once the write worked. */
    suspend fun moveToSavedNow(id: String): Boolean {
        val saved = try {
            store.moveToSaved(id)
        } catch (_: SavedPlacement.CannotSave) {
            null
        }
        _lastSaved.value = saved?.let { SavedSnapshot(it.id, it.title) }
        return saved != null
    }

    /** Undo puts the task back where it was: Save changed nothing but the placement. */
    fun undoSave() {
        val snapshot = _lastSaved.value ?: return
        viewModelScope.launch {
            store.change(snapshot.id) { it.copy(savedAt = null) }
            _lastSaved.value = null
        }
    }

    /** Ends one Undo offer. A newer save may have replaced it, and that one stays. */
    fun clearSavedUndo(shown: SavedSnapshot) {
        _lastSaved.compareAndSet(shown, null)
    }

    private val _lastUrgent = MutableStateFlow<UrgentSnapshot?>(null)
    val lastUrgent: StateFlow<UrgentSnapshot?> = _lastUrgent.asStateFlow()

    /**
     * Mark urgent from an escalating card (spec-escalation.md §5): an ordinary edit through
     * [TaskStore.change], so it syncs like any other. Only un-urgent tasks are offered it, which is
     * what makes Undo's `urgent = false` the exact inverse.
     */
    fun markUrgent(id: String) {
        viewModelScope.launch { markUrgentNow(id) }
    }

    /** [markUrgent] for a caller that moves on only once the write worked. */
    suspend fun markUrgentNow(id: String): Boolean {
        val current = store.get(id) ?: return false
        if (current.urgent) return false
        val changed = store.change(id) { it.copy(urgent = true) } ?: return false
        _lastUrgent.value = UrgentSnapshot(changed.id, changed.title, changed.quadrant)
        return true
    }

    fun undoMarkUrgent() {
        val snapshot = _lastUrgent.value ?: return
        viewModelScope.launch {
            store.change(snapshot.id) { it.copy(urgent = false) }
            _lastUrgent.value = null
        }
    }

    /** Ends one Undo offer. A newer Mark urgent may have replaced it, and that one stays. */
    fun clearUrgentUndo(shown: UrgentSnapshot) {
        _lastUrgent.compareAndSet(shown, null)
    }

    /** Drag-and-drop reclassification (spec §6.2): the flags change, quadrant follows. */
    fun moveToQuadrant(id: String, quadrant: Quadrant) {
        viewModelScope.launch { moveToQuadrantNow(id, quadrant) }
    }

    /** [moveToQuadrant] for a caller that moves on only once the write worked. */
    suspend fun moveToQuadrantNow(id: String, quadrant: Quadrant): Boolean {
        val current = store.get(id) ?: return false
        if (current.quadrant == quadrant) return false
        return store.change(id) { it.copy(urgent = quadrant.urgent, important = quadrant.important) } != null
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
