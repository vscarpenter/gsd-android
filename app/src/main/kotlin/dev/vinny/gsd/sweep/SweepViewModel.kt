package dev.vinny.gsd.sweep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.SweepReason
import dev.vinny.gsd.model.SweepSession
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TopThree
import dev.vinny.gsd.model.WeeklySweep
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.TaskStore
import android.util.Log
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SweepState {
    data object Loading : SweepState

    /** [position] counts from 1 over the cards computed when the sweep opened. */
    data class Card(val task: Task, val reason: SweepReason, val position: Int, val total: Int) : SweepState

    /** [offerTopThree] is true while today has a free slot (spec-weekly-sweep.md §7, the end offer). */
    data class End(val offerTopThree: Boolean) : SweepState
}

/**
 * One weekly sweep (spec-weekly-sweep.md §4). The cards are computed once when it opens and never
 * reordered. Before each card shows, the task is checked again. Opening, skipping, and leaving write
 * nothing to any task: the screen runs each action through the path that already exists, then calls
 * [advance]. The only thing this class writes is "last swept", on reaching the end or on Done.
 */
class SweepViewModel(
    private val store: TaskStore,
    private val prefs: GsdPrefs,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _state = MutableStateFlow<SweepState>(SweepState.Loading)
    val state: StateFlow<SweepState> = _state.asStateFlow()

    private var cards = emptyList<dev.vinny.gsd.model.SweepCard>()
    private var index = -1
    private var stamped = false

    init {
        viewModelScope.launch {
            val tasks = store.observeTasks().first()
            cards = WeeklySweep.cards(tasks, now(), prefs.escalationWindow.first(), clock.zone)
            show(after = -1)
        }
    }

    private fun now(): Instant = clock.instant()

    /** Moves past the current card to the next one that still qualifies, or to the end. */
    fun advance() {
        viewModelScope.launch { show(after = index) }
    }

    fun skip() = advance()

    /** Done stamps "last swept" when there were cards, then the screen leaves. */
    fun done(onLeave: () -> Unit) {
        viewModelScope.launch {
            if (SweepSession.stampsLastSwept(cards.size, reachedEnd = false, tappedDone = true)) stamp()
            onLeave()
        }
    }

    private suspend fun show(after: Int) {
        val tasks = store.observeTasks().first()
        val window = prefs.escalationWindow.first()
        val qualifying = WeeklySweep.cards(tasks, now(), window, clock.zone).associateBy { it.id }
        val next = SweepSession.nextIndex(cards, after, qualifying.keys)
        index = next
        if (next >= cards.size) {
            if (SweepSession.stampsLastSwept(cards.size, reachedEnd = true, tappedDone = false)) stamp()
            _state.value = SweepState.End(offerTopThree = TopThree.nextRank(tasks, now(), clock.zone) != null)
            return
        }
        val card = cards[next]
        val task = tasks.first { it.id == card.id }
        _state.value = SweepState.Card(task, qualifying.getValue(card.id).reason, position = next + 1, total = cards.size)
    }

    private suspend fun stamp() {
        if (stamped) return
        stamped = true
        prefs.setLastSweptAt(now())
    }

    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One tick per write that failed, so the screen can say so and stay on the card. */
    val failures: SharedFlow<Unit> = _failures.asSharedFlow()

    /**
     * Runs an action's write and moves on only when it worked (spec decision S12). A write that returns
     * false or throws leaves the card where it is and reports a failure.
     */
    fun advanceAfter(write: suspend () -> Boolean) {
        viewModelScope.launch {
            val worked = try {
                write()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("GsdSweep", "sweep action failed: ${error.javaClass.simpleName}")
                false
            }
            if (worked) show(after = index) else _failures.emit(Unit)
        }
    }

    // The writes below are the paths the sweep shares with the rest of the app, not new ones.

    fun complete(id: String) = advanceAfter { store.setCompleted(id, true) != null }

    fun setDue(id: String, due: Instant?) = advanceAfter { store.change(id) { it.copy(dueDate = due) } != null }

    fun returnToMatrix(id: String, urgent: Boolean, important: Boolean, due: Instant?) =
        advanceAfter { store.returnToMatrix(id, urgent, important, due) != null }
}
