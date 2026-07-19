package dev.vinny.gsd.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskDuplicator
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.TaskStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QuadrantSectionState(
    val quadrant: Quadrant,
    val activeCount: Int,
    val tasks: List<Task>,
)

/**
 * Pure grouping: Q1→Q4 order, incomplete above completed (spec §8.1),
 * header counts are active-only regardless of the show-completed toggle.
 */
fun buildSections(tasks: List<Task>, showCompleted: Boolean = true): List<QuadrantSectionState> =
    Quadrant.entries.map { quadrant ->
        val inQuadrant = tasks.filter { it.quadrant == quadrant }
        val visible = if (showCompleted) inQuadrant else inQuadrant.filter { !it.completed }
        QuadrantSectionState(
            quadrant = quadrant,
            activeCount = inQuadrant.count { !it.completed },
            tasks = visible.sortedBy { it.completed },
        )
    }

class MatrixViewModel(private val store: TaskStore, prefs: GsdPrefs) : ViewModel() {
    val sections: StateFlow<List<QuadrantSectionState>> =
        combine(store.observeTasks(), prefs.showCompleted) { tasks, showCompleted ->
            buildSections(tasks, showCompleted)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), buildSections(emptyList()))

    fun toggleCompleted(id: String) {
        viewModelScope.launch {
            val current = store.get(id) ?: return@launch
            store.setCompleted(id, !current.completed)
        }
    }

    private val _lastDeleted = MutableStateFlow<Task?>(null)
    val lastDeleted: StateFlow<Task?> = _lastDeleted.asStateFlow()

    /** Delete commits immediately (spec §8.20) — the snackbar offers undo after the fact. */
    fun delete(id: String) {
        viewModelScope.launch { _lastDeleted.value = store.delete(id) }
    }

    /** Undo re-creates from the snapshot as a fresh write — new updatedAt, LWW-safe. */
    fun undoDelete() {
        val snapshot = _lastDeleted.value ?: return
        viewModelScope.launch {
            store.add(snapshot)
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
            store.update(current.copy(urgent = quadrant.urgent, important = quadrant.important))
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            val current = store.get(id) ?: return@launch
            store.add(TaskDuplicator.duplicate(current, java.time.Instant.now()))
        }
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
