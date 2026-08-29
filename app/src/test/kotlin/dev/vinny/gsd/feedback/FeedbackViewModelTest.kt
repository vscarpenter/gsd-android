package dev.vinny.gsd.feedback

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.vinny.gsd.model.FeedbackCategory
import dev.vinny.gsd.model.FeedbackPayload
import dev.vinny.gsd.model.Sentiment
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.sync.FeedbackApi
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FeedbackViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var api: FeedbackApi
    private lateinit var prefs: GsdPrefs
    private lateinit var viewModel: FeedbackViewModel
    private lateinit var storeFile: File
    private val at = Instant.parse("2026-08-29T12:00:00.000Z")

    @Before
    fun setUp() {
        // FeedbackApi.submit hops onto the real Dispatchers.IO for the OkHttp call, so Main is set
        // to a real (not virtual-time) dispatcher — assertions after send() await the terminal
        // state explicitly (awaitResult below) rather than racing a test-scheduler "idle" flush.
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer()
        server.start()
        api = FeedbackApi(client = OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
        storeFile = File.createTempFile("feedback-prefs", ".preferences_pb").also { it.deleteOnExit() }
        prefs = GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { storeFile }))
        viewModel = FeedbackViewModel(api, prefs, appVersion = "android-0.7.0.7", now = { at })
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
        storeFile.delete()
    }

    /** Draft edits land on [FeedbackViewModel.state] synchronously; only send() crosses a real dispatcher. */
    private suspend fun awaitResult(): FeedbackUiState =
        withTimeout(5_000) { viewModel.state.first { it.sendState != FeedbackSendState.Sending } }

    @Test
    fun `the preview mirrors draft edits and is exactly what send POSTs`() = runBlocking {
        viewModel.setSentiment(Sentiment.UP)
        viewModel.setCategory(FeedbackCategory.IDEA)
        viewModel.setMessage("more focus please")
        viewModel.toggleVote("wear-os")

        val preview = viewModel.state.value.preview
        assertEquals(
            FeedbackPayload.build(viewModel.state.value.draft, "android-0.7.0.7", at),
            preview,
        )

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        viewModel.send()
        awaitResult()

        val recorded = server.takeRequest()
        val sentBody = recorded.body.readUtf8()
        assertEquals(preview.toString(), sentBody) // preview and payload never drift
    }

    @Test
    fun `send is disabled while the draft is empty`() = runBlocking {
        assertTrue(viewModel.state.value.draft.isEmpty)
        // no request enqueued — a send while empty must be a no-op, not a hang
        viewModel.send()
        assertEquals(FeedbackSendState.Idle, viewModel.state.value.sendState)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a successful send clears the draft and mints a fresh submission id`() = runBlocking {
        val originalId = viewModel.state.value.draft.submissionId
        viewModel.setSentiment(Sentiment.DOWN)
        viewModel.setMessage("needs work")

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        viewModel.send()
        val result = awaitResult()

        assertEquals(FeedbackSendState.Sent, result.sendState)
        assertTrue(result.draft.isEmpty)
        assertTrue(result.draft.submissionId != originalId)

        // the sent draft is wiped from disk entirely — the next launch mints its own fresh id
        assertEquals(null, prefs.feedbackDraft.first())
    }

    @Test
    fun `a failed send keeps every field so retry can collapse server-side`() = runBlocking {
        viewModel.setSentiment(Sentiment.UP)
        viewModel.setMessage("still here")
        val beforeSend = viewModel.state.value.draft

        server.enqueue(MockResponse().setResponseCode(500))
        viewModel.send()
        val afterFailure = awaitResult()

        assertTrue(afterFailure.sendState is FeedbackSendState.Failed)
        assertEquals(beforeSend, afterFailure.draft) // nothing lost, same submission id for the retry
    }
}
