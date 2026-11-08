package dev.vinny.gsd.matrix

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.TodayView
import dev.vinny.gsd.model.TopThree
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.TaskStore
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Today section's state and its four writes (spec-top-three.md §3.2). Every write is an ordinary edit
 * through [TaskStore.change], so it stamps `updatedAt` and queues a push. Nothing writes at midnight: the
 * clock only re-reads the date, so yesterday's picks stop counting.
 */
class TodayViewModel(
    private val store: TaskStore,
    prefs: GsdPrefs,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val now = MutableStateFlow(clock.instant())

    val view: StateFlow<TodayView?> =
        combine(store.observeTasks(), prefs.escalationWindow, now) { tasks, window, instant ->
            TodayView.build(tasks, instant, window, clock.zone)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refusals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One event per Add to today that found the day full, so the screen can say "Today is full". */
    val refusals: SharedFlow<Unit> = _refusals.asSharedFlow()

    private val _tidyDismissed = MutableStateFlow(false)

    /** "Not now" writes nothing and lasts until the next launch (spec §4). */
    val tidyDismissed: StateFlow<Boolean> = _tidyDismissed.asStateFlow()

    init {
        // A sleeping device can overshoot this delay, so the screen also calls refreshNow() when it resumes
        viewModelScope.launch {
            while (true) {
                val instant = clock.instant()
                val midnight = instant.atZone(clock.zone).toLocalDate().plusDays(1).atStartOfDay(clock.zone)
                delay(Duration.between(instant, midnight).toMillis() + 1_000)
                now.value = clock.instant()
            }
        }
    }

    fun refreshNow() {
        now.value = clock.instant()
    }

    fun dismissTidy() {
        _tidyDismissed.value = true
    }

    fun addToToday(id: String) {
        viewModelScope.launch {
            val tasks = store.observeTasks().first()
            // The add hides when the day is full, so only a race lands here, and it says so (web T15)
            val pick = TopThree.addWrite(tasks, clock.instant(), clock.zone)
            if (pick == null) {
                _refusals.emit(Unit)
                return@launch
            }
            store.change(id, refuseSaved = true) { it.copy(focusDate = pick.focusDate, focusRank = pick.focusRank) }
        }
    }

    fun removeFromToday(id: String) {
        viewModelScope.launch {
            store.change(id) { it.copy(focusDate = null, focusRank = null) }
        }
    }

    fun move(id: String, move: TopThree.Move) {
        viewModelScope.launch {
            val tasks = store.observeTasks().first()
            for (write in TopThree.moveWrites(tasks, id, move, clock.instant(), clock.zone)) {
                store.change(write.id) { it.copy(focusDate = write.focusDate, focusRank = write.focusRank) }
            }
        }
    }

    fun tidy() {
        viewModelScope.launch {
            val tasks = store.observeTasks().first()
            for (write in TopThree.tidy(tasks, clock.instant(), clock.zone)) {
                store.change(write.id) { it.copy(focusDate = write.focusDate, focusRank = write.focusRank) }
            }
        }
    }
}
