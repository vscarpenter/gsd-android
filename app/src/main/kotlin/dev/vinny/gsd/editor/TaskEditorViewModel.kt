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

    var isNew = true
        private set
    private var original: Task? = null

    /** The draft as the sheet opened it, new or loaded, to tell an edited draft from an untouched one. */
    private var opened: Task? = null

    /**
     * New task: the ID is reserved at editor-open (spec §8.3). An open draft wins, so a shortcut or
     * a widget tap that lands while the sheet is up never wipes what the user has typed.
     */
    fun openNew(prefill: CaptureState?) {
        if (_draft.value != null) return
        val parsed = prefill?.parsed
        val quadrant = prefill?.effectiveQuadrant ?: Quadrant.ELIMINATE
        isNew = true
        val fresh = Task(
            title = parsed?.title.orEmpty(),
            description = parsed?.urls?.joinToString("\n").orEmpty(),
            urgent = quadrant.urgent,
            important = quadrant.important,
            tags = parsed?.tags.orEmpty(),
            createdAt = Instant.EPOCH, // store stamps on save
            updatedAt = Instant.EPOCH,
        )
        opened = fresh
        _draft.value = fresh
    }

    fun openExisting(id: String, onMissing: () -> Unit = {}) {
        isNew = false
        viewModelScope.launch {
            original = store.get(id)
            opened = original
            _draft.value = original
            if (original == null) onMissing() // friendly not-found, never a blank screen (§6.4)
        }
    }

    fun edit(transform: (Task) -> Task) {
        _draft.value = _draft.value?.let(transform)
    }

    /**
     * A new task is added whole. An existing task is merged onto the row as it is now (spec §8.3), so
     * a completion, a timer, or a snooze that landed while the sheet was open survives the save.
     * [onMissing] runs when the task was deleted in the meantime. Nothing is written then.
     */
    fun save(onDone: () -> Unit, onError: (String) -> Unit, onMissing: () -> Unit = {}) {
        val edited = _draft.value ?: return
        val loaded = original
        viewModelScope.launch {
            try {
                val saved = if (isNew || loaded == null) {
                    store.add(edited)
                } else {
                    store.edit(edited.id) { current -> EditorRules.merge(current, loaded, edited) }
                }
                _draft.value = null
                if (saved == null) onMissing() else onDone()
            } catch (error: TaskValidationError) {
                onError(error.message ?: "Couldn't save task")
            }
        }
    }

    /**
     * Whether the open draft differs from how the sheet opened it. Closing then asks first, since a
     * swipe down used to throw the edits away (review finding 25).
     */
    fun hasUnsavedChanges(): Boolean = _draft.value.let { it != null && it != opened }

    fun dismiss() {
        _draft.value = null
    }
}
