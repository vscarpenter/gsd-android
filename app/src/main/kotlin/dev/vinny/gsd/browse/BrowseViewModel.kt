package dev.vinny.gsd.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.SmartViewStore
import dev.vinny.gsd.store.TaskStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BrowseRow(val view: SmartView, val count: Int, val pinned: Boolean)

data class BrowseState(
    val pinned: List<BrowseRow> = emptyList(),
    val builtIn: List<BrowseRow> = emptyList(),
    val custom: List<BrowseRow> = emptyList(),
)

/** Pure grouping: pinned first (pin order), then built-ins, then custom (spec §6.1). */
fun buildBrowseState(
    views: List<SmartView>,
    pinnedIds: List<String>,
    tasks: List<Task>,
    now: Instant,
    zone: ZoneId,
): BrowseState {
    val rows = views.associateWith { view ->
        tasks.count { view.criteria.matches(it, tasks, now, zone) }
    }
    fun row(view: SmartView) = BrowseRow(view, rows.getValue(view), view.id in pinnedIds)
    val pinned = pinnedIds.mapNotNull { id -> views.firstOrNull { it.id == id } }.map(::row)
    val remaining = views.filter { it.id !in pinnedIds }
    return BrowseState(
        pinned = pinned,
        builtIn = remaining.filter { it.isBuiltIn }.map(::row),
        custom = remaining.filter { !it.isBuiltIn }.map(::row),
    )
}

class BrowseViewModel(
    private val store: TaskStore,
    private val smartViews: SmartViewStore,
) : ViewModel() {
    init {
        viewModelScope.launch { smartViews.seedBuiltInsIfEmpty() }
    }

    val state: StateFlow<BrowseState> =
        combine(smartViews.observeViews(), smartViews.observePinned(), store.observeTasks()) { views, pins, tasks ->
            buildBrowseState(views, pins, tasks, Instant.now(), ZoneId.systemDefault())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrowseState())

    /** `null` until the first snapshot loads — lets a smart-view list tell "loading" from "empty" (S§4.2). */
    val tasks: StateFlow<List<Task>?> =
        store.observeTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun togglePin(id: String) {
        viewModelScope.launch {
            val current = smartViews.pinned()
            if (id in current) {
                smartViews.setPinned(current - id)
            } else if (current.size < 5) {
                smartViews.setPinned(current + id)
            }
        }
    }

    fun deleteCustom(id: String) {
        viewModelScope.launch { smartViews.delete(id) }
    }

    fun toggleCompleted(id: String) {
        viewModelScope.launch {
            val current = store.get(id) ?: return@launch
            store.setCompleted(id, !current.completed)
        }
    }
}
