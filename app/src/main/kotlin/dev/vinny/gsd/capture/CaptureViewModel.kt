package dev.vinny.gsd.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.store.TaskStore
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CaptureViewModel(private val store: TaskStore) : ViewModel() {
    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    fun onTextChange(text: String) {
        _state.value = _state.value.copy(text = text)
    }

    fun cycleOverride() {
        _state.value = _state.value.cycled()
    }

    /** Adds the parsed task; keeps the raw text in place on validation failure (spec §8.2). */
    fun submit(onError: (String) -> Unit) {
        val current = _state.value
        val parsed = current.parsed
        if (parsed.title.isEmpty()) return
        val quadrant = current.effectiveQuadrant
        viewModelScope.launch {
            try {
                store.add(
                    Task(
                        title = parsed.title,
                        description = parsed.urls.joinToString("\n"),
                        urgent = quadrant.urgent,
                        important = quadrant.important,
                        tags = parsed.tags,
                        createdAt = Instant.EPOCH, // store stamps
                        updatedAt = Instant.EPOCH,
                    ),
                )
                _state.value = CaptureState() // clear for rapid serial capture
            } catch (error: TaskValidationError) {
                onError(error.message ?: "Couldn't add task")
            }
        }
    }

    /** Hands the current draft to the full editor and consumes it (spec §8.2). */
    fun draftForEditor(): CaptureState {
        val draft = _state.value
        _state.value = CaptureState()
        return draft
    }
}
