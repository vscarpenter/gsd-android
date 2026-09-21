package dev.vinny.gsd.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
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

class MatrixViewModel(
    private val store: TaskStore,
    prefs: GsdPrefs,
) : ViewModel() {
    val sections: StateFlow<List<QuadrantSectionState>?> =
        combine(store.observeTasks(), prefs.showCompleted) { tasks, showCompleted ->
            buildSections(tasks, showCompleted)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // --- Multi-select + bulk (spec §8.11) ---
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _lastBulkResult = MutableStateFlow<BulkResult?>(null)
    val lastBulkResult: StateFlow<BulkResult?> = _lastBulkResult.asStateFlow()

    fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        if (!_selectionMode.value) _selection.value = emptySet()
    }

    fun toggleSelected(id: String) {
        _selection.value = if (id in _selection.value) _selection.value - id else _selection.value + id
    }

    fun runBulk(op: BulkOp) {
        viewModelScope.launch {
            _lastBulkResult.value = BulkActions.apply(store, _selection.value, op)
            _selection.value = emptySet()
            _selectionMode.value = false
        }
    }

    fun clearBulkResult() {
        _lastBulkResult.value = null
    }
}
