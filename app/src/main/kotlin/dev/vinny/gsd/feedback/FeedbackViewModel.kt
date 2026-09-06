package dev.vinny.gsd.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.vinny.gsd.model.FeedbackCategory
import dev.vinny.gsd.model.FeedbackDraft
import dev.vinny.gsd.model.FeedbackPayload
import dev.vinny.gsd.model.NanoId
import dev.vinny.gsd.model.Sentiment
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.sync.FeedbackApi
import dev.vinny.gsd.sync.FeedbackFailure
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/** Send-flow status for the feedback screen (parity spec P§6.2). */
sealed interface FeedbackSendState {
    data object Idle : FeedbackSendState
    data object Sending : FeedbackSendState
    data object Sent : FeedbackSendState
    data class Failed(val kind: FeedbackFailure) : FeedbackSendState
}

data class FeedbackUiState(
    val draft: FeedbackDraft,
    /** The exact object [send] will POST — the "what gets sent" disclosure renders this verbatim. */
    val preview: JsonObject,
    val sendState: FeedbackSendState = FeedbackSendState.Idle,
)

/**
 * Anonymous opt-in feedback (parity spec P§6.1–§6.2). The draft persists across process
 * death via [GsdPrefs] under one durable [FeedbackDraft.submissionId] so a retried send
 * collapses server-side instead of double-submitting. The disclosure preview and the
 * submitted payload are always the same [JsonObject] — never rebuilt independently.
 */
class FeedbackViewModel(
    private val api: FeedbackApi,
    private val prefs: GsdPrefs,
    private val appVersion: String,
    private val now: () -> Instant = Instant::now,
) : ViewModel() {
    private val _state = MutableStateFlow(stateFor(FeedbackDraft(submissionId = NanoId.generate())))
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    // Both init's load and edit()/send() run on the same Main-confined dispatcher (standard
    // ViewModel/viewModelScope assumption), so a plain var is safe here — no atomics needed.
    private var touched = false

    init {
        viewModelScope.launch {
            // The in-memory default already carries a freshly-minted id; only a REAL persisted
            // draft needs to replace it, and only if the caller hasn't already touched the screen.
            // This load is async (real disk IO) and can resolve after in-flight edits — applying it
            // unconditionally would silently discard whatever the caller did in the meantime.
            when (val stored = prefs.feedbackDraft.first()) {
                null -> prefs.setFeedbackDraft(_state.value.draft)
                else -> if (!touched) _state.value = stateFor(stored)
            }
        }
    }

    private fun stateFor(draft: FeedbackDraft, sendState: FeedbackSendState = FeedbackSendState.Idle) =
        FeedbackUiState(draft = draft, preview = FeedbackPayload.build(draft, appVersion, now()), sendState = sendState)

    private fun edit(transform: (FeedbackDraft) -> FeedbackDraft) {
        touched = true
        val draft = transform(_state.value.draft)
        _state.value = stateFor(draft) // editing after Sent/Failed returns the screen to Idle
        viewModelScope.launch { prefs.setFeedbackDraft(draft) }
    }

    fun setSentiment(sentiment: Sentiment?) = edit { it.copy(sentiment = sentiment) }

    fun setCategory(category: FeedbackCategory?) = edit { it.copy(category = category) }

    fun setMessage(message: String) = edit { it.copy(message = message.take(FeedbackPayload.MAX_MESSAGE_LENGTH)) }

    fun toggleVote(slug: String) = edit { draft ->
        draft.copy(votes = if (slug in draft.votes) draft.votes - slug else draft.votes + slug)
    }

    /** Send is a no-op while the draft is empty or already in flight (P§6.2). */
    fun send() {
        val current = _state.value
        if (current.draft.isEmpty || current.sendState == FeedbackSendState.Sending) return
        touched = true
        _state.value = current.copy(sendState = FeedbackSendState.Sending)
        viewModelScope.launch {
            when (val result = api.submit(current.preview)) {
                FeedbackApi.Result.Sent -> {
                    prefs.clearFeedbackDraft()
                    // next draft's id is minted now so the screen stays usable immediately;
                    // it's only persisted once the caller actually edits or sends again.
                    _state.value = stateFor(FeedbackDraft(submissionId = NanoId.generate()), FeedbackSendState.Sent)
                }
                is FeedbackApi.Result.Failed -> {
                    // draft (and its submission id) is kept as-is so a retry collapses server-side
                    _state.value = stateFor(current.draft, FeedbackSendState.Failed(result.kind))
                }
            }
        }
    }
}
