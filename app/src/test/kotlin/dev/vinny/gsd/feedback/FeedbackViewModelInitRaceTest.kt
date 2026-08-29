package dev.vinny.gsd.feedback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.vinny.gsd.model.FeedbackDraft
import dev.vinny.gsd.model.Sentiment
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.sync.FeedbackApi
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * FeedbackViewModel.init reads a possibly-persisted draft asynchronously (parity spec P§6.2).
 * That read must never clobber an edit the caller makes while it's still in flight. These tests
 * pin the exact interleaving deterministically with a controllable [DataStore] fake plus a
 * [StandardTestDispatcher] — "before the load resolves" is an explicit gate here, not a real-IO
 * race (see FeedbackViewModelTest for why real IO + a virtual-time scheduler don't mix).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackViewModelInitRaceTest {
    private val dispatcher = StandardTestDispatcher()
    private val at = Instant.parse("2026-08-29T12:00:00.000Z")

    // send() is never called by these tests — an API instance is only needed to satisfy the
    // ViewModel's constructor.
    private val api = FeedbackApi()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Obtains a genuine seeded [Preferences] value via a real, one-time, non-gated temp-file
     * DataStore (there's no public API to construct a [Preferences] directly — its factories and
     * mutable constructor are Kotlin-internal to the datastore module), then hands it to the fully
     * in-memory [ControllableDataStore]. From that point on nothing in the test touches real IO —
     * the init-load/edit race itself is 100% coroutine-primitive and dispatcher-controlled.
     */
    private fun seededStore(): ControllableDataStore {
        val bootstrapFile = File.createTempFile("feedback-race-prefs", ".preferences_pb").also { it.deleteOnExit() }
        val bootstrap = PreferenceDataStoreFactory.create(produceFile = { bootstrapFile })
        val seeded = runBlocking {
            GsdPrefs(bootstrap).setFeedbackDraft(
                FeedbackDraft(sentiment = Sentiment.UP, message = "stored", submissionId = "stored-id"),
            )
            bootstrap.data.first()
        }
        return ControllableDataStore(seeded)
    }

    @Test
    fun `an edit that lands before the stored draft resolves is not clobbered`() {
        val store = seededStore()
        val viewModel = FeedbackViewModel(api, GsdPrefs(store), appVersion = "android-0.7.0.7", now = { at })
        dispatcher.scheduler.advanceUntilIdle() // init runs up to the gated read, then parks there

        viewModel.setSentiment(Sentiment.DOWN) // the user edits while the load is still in flight

        store.release()
        dispatcher.scheduler.advanceUntilIdle() // the load resolves now, with `touched` already true

        val draft = viewModel.state.value.draft
        assertEquals(Sentiment.DOWN, draft.sentiment) // the user's edit survived
        assertNotEquals("stored", draft.message) // the stale persisted draft was NOT applied over it
    }

    @Test
    fun `the stored draft is applied when the caller hasn't touched anything`() {
        val store = seededStore()
        val viewModel = FeedbackViewModel(api, GsdPrefs(store), appVersion = "android-0.7.0.7", now = { at })
        dispatcher.scheduler.advanceUntilIdle() // parks on the gated read

        store.release()
        dispatcher.scheduler.advanceUntilIdle() // resolves — nothing raced it

        val draft = viewModel.state.value.draft
        assertEquals(Sentiment.UP, draft.sentiment)
        assertEquals("stored", draft.message)
        assertEquals("stored-id", draft.submissionId)
    }
}

/** In-memory [DataStore] whose [data] flow suspends until [release] is called. */
private class ControllableDataStore(initial: Preferences) : DataStore<Preferences> {
    private val gate = CompletableDeferred<Unit>()
    private var current: Preferences = initial

    override val data: Flow<Preferences> = flow {
        // Snapshot at subscription time, not emission time: a real disk read that started before
        // a later write reflects the pre-write content, not whatever landed while it was in flight.
        val snapshot = current
        gate.await()
        emit(snapshot)
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        current = transform(current)
        return current
    }

    fun release() {
        gate.complete(Unit)
    }
}
