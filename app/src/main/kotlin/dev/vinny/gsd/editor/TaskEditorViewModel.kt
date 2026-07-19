package dev.vinny.gsd.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.capture.CaptureState
import dev.vinny.gsd.model.EditorRules
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.store.TaskStore
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskEditorViewModel(private val store: TaskStore) : ViewModel() {
    private val _draft = MutableStateFlow<Task?>(null)
    val draft: StateFlow<Task?> = _draft.asStateFlow()

    val allTasks: StateFlow<List<Task>> = store.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var isNew = true
    private var original: Task? = null

    /** New task: the ID is reserved at editor-open (spec §8.3). */
    fun openNew(prefill: CaptureState?) {
        val parsed = prefill?.parsed
        val quadrant = prefill?.effectiveQuadrant ?: Quadrant.ELIMINATE
        isNew = true
        _draft.value = Task(
            title = parsed?.title.orEmpty(),
            description = parsed?.urls?.joinToString("\n").orEmpty(),
            urgent = quadrant.urgent,
            important = quadrant.important,
            tags = parsed?.tags.orEmpty(),
            createdAt = Instant.EPOCH, // store stamps on save
            updatedAt = Instant.EPOCH,
        )
    }

    fun openExisting(id: String) {
        isNew = false
        viewModelScope.launch {
            original = store.get(id)
            _draft.value = original
        }
    }

    fun edit(transform: (Task) -> Task) {
        _draft.value = _draft.value?.let(transform)
    }

    fun save(onDone: () -> Unit, onError: (String) -> Unit) {
        val edited = _draft.value ?: return
        // Editing dueDate/notifyBefore resets reminder state (spec §8.3)
        val task = original?.let { EditorRules.applyReminderReset(it, edited) } ?: edited
        viewModelScope.launch {
            try {
                if (isNew) store.add(task) else store.update(task)
                _draft.value = null
                onDone()
            } catch (error: TaskValidationError) {
                onError(error.message ?: "Couldn't save task")
            }
        }
    }

    fun dismiss() {
        _draft.value = null
    }
}
