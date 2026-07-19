package dev.vinny.gsd.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.TaskStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
}
