package dev.vinny.gsd.sync

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PocketBaseApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PocketBaseApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = PocketBaseApi(baseUrl = server.url("/").toString().trimEnd('/'), token = { "raw-jwt-token" })
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `auth header is the raw jwt with no bearer prefix`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"page":1,"perPage":200,"totalItems":0,"totalPages":1,"items":[]}"""))
        api.listTasks(page = 1, updatedCursor = "1970-01-01 00:00:00.000Z")
        val recorded = server.takeRequest()
        assertEquals("raw-jwt-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `list encodes the cursor filter and paging params`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"page":1,"perPage":200,"totalItems":1,"totalPages":1,
               "items":[{"task_id":"t1","client_updated_at":"2026-06-10T12:00:00.000Z","updated":"2026-06-10 12:00:00.123Z"}]}""",
        ))
        val result = api.listTasks(page = 1, updatedCursor = "2026-06-10 11:00:00.000Z")
        val recorded = server.takeRequest()
        val path = recorded.path!!
        assertTrue("perPage=200" in path)
        assertEquals("updated,id", recorded.requestUrl!!.queryParameter("sort"))
        assertTrue("filter=" in path)
        assertTrue("%22" in path || "\"" !in path) // quotes URL-encoded
        assertEquals(1, result.totalPages)
        assertEquals("t1", result.items.single().taskId)
    }

    @Test
    fun `list decodes totalItems from the envelope`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"page":1,"perPage":200,"totalItems":7,"totalPages":1,"items":[]}"""))
        val result = api.listTasks(page = 1, updatedCursor = "1970-01-01 00:00:00.000Z")
        assertEquals(7, result.totalItems)
    }

    @Test
    fun `create posts empty record id and returns the created record`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"rec123456789012","task_id":"t1","client_updated_at":"2026-06-10T12:00:00.000Z"}"""))
        val wire = WireTask(taskId = "t1", clientUpdatedAt = "2026-06-10T12:00:00.000Z")
        val created = api.createTask(wire)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue("\"id\":\"\"" in recorded.body.readUtf8().replace(" ", ""))
        assertEquals("rec123456789012", created.id)
    }

    @Test
    fun `update patches by record id and delete expects 204`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"rec1","task_id":"t1","client_updated_at":"2026-06-10T12:00:00.000Z"}"""))
        api.updateTask("rec1", WireTask(taskId = "t1"))
        val patch = server.takeRequest()
        assertEquals("PATCH", patch.method)
        // an empty id in the PATCH body reads as a primary-key change → 400 validation_pk_change
        assertTrue("\"id\":\"rec1\"" in patch.body.readUtf8().replace(" ", ""))

        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteTask("rec1")
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertTrue(recorded.path!!.endsWith("/rec1"))
    }

    @Test
    fun `429 raises the typed rate-limit error`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        assertFailsWith<PocketBaseApi.RateLimited> {
            api.listTasks(page = 1, updatedCursor = "1970-01-01 00:00:00.000Z")
        }
    }

    @Test
    fun `definitive auth failures surface as unauthorized`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertFailsWith<PocketBaseApi.Unauthorized> {
            api.authRefresh()
        }
    }

    @Test
    fun `malformed items are skipped, not fatal`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            """{"page":1,"perPage":200,"totalItems":2,"totalPages":1,
               "items":[{"nonsense":true},{"task_id":"ok","client_updated_at":"2026-06-10T12:00:00.000Z"}]}""",
        ))
        val result = api.listTasks(page = 1, updatedCursor = "1970-01-01 00:00:00.000Z")
        assertEquals(1, result.items.size)
        assertNull(result.items.firstOrNull { it.taskId != "ok" })
    }
}
