package dev.vinny.gsd.sync

import dev.vinny.gsd.model.FeedbackCategory
import dev.vinny.gsd.model.FeedbackDraft
import dev.vinny.gsd.model.FeedbackPayload
import dev.vinny.gsd.model.Sentiment
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedbackApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: FeedbackApi
    private lateinit var payload: JsonObject

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = FeedbackApi(client = OkHttpClient(), baseUrl = server.url("/").toString().trimEnd('/'))
        payload = FeedbackPayload.build(
            FeedbackDraft(
                sentiment = Sentiment.UP,
                category = FeedbackCategory.IDEA,
                message = "more focus",
                submissionId = "sub-1",
            ),
            appVersion = "android-0.7.0.7",
            submittedAt = Instant.parse("2026-08-29T12:00:00.000Z"),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `submit posts JSON to the feedback collection with no auth header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = api.submit(payload)
        val recorded = server.takeRequest()
        assertEquals("/api/collections/feedback/records", recorded.path)
        assertEquals("application/json; charset=utf-8", recorded.getHeader("Content-Type"))
        assertNull(recorded.getHeader("Authorization")) // anonymity is the contract
        assertEquals(FeedbackApi.Result.Sent, result)
    }

    @Test
    fun `a duplicate submission id collapses to success`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"data":{"submission_id":{"code":"validation_not_unique","message":"..."}}}"""),
        )
        assertEquals(FeedbackApi.Result.Sent, api.submit(payload)) // earlier attempt landed (P§6.1)
    }

    @Test
    fun `other failures keep the draft`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(api.submit(payload) is FeedbackApi.Result.Failed)
    }

    @Test
    fun `a validation error on a different field is not mistaken for the duplicate collapse`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"data":{"message":{"code":"validation_required","message":"..."}}}"""),
        )
        assertTrue(api.submit(payload) is FeedbackApi.Result.Failed) // structured check, not a substring scan
    }

    @Test
    fun `network failure is a Failed result, never a thrown exception`() = runBlocking {
        server.shutdown()
        assertTrue(api.submit(payload) is FeedbackApi.Result.Failed)
    }
}
