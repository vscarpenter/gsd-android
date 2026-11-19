package dev.vinny.gsd.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private val guardedAllowList = setOf(
        "owner", "task_id", "title", "description", "urgent", "important", "quadrant", "due_date",
        "completed", "completed_at", "recurrence", "tags", "subtasks", "dependencies",
        "notification_enabled", "notification_sent", "notify_before", "last_notification_at",
        "estimated_minutes", "time_spent", "time_entries", "snoozed_until", "saved_at", "focus_date",
        "focus_rank", "client_updated_at", "client_created_at", "device_id",
    )

    @Test
    fun `guarded update PATCHes the task route with the version and no id, created, or updated`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.updateTaskGuarded("rec1", "v1", WireTask(id = "rec1", taskId = "t1", created = "c", updated = "u"))
        val patch = server.takeRequest()
        assertEquals("/api/gsd/tasks/rec1", patch.path)
        assertEquals("PATCH", patch.method)
        val body = Json.parseToJsonElement(patch.body.readUtf8()).jsonObject
        assertEquals("v1", body["expected_version"]!!.jsonPrimitive.content)
        val data = body["data"]!!.jsonObject
        assertEquals("t1", data["task_id"]!!.jsonPrimitive.content)
        assertTrue(data.keys.all { it in guardedAllowList }, "off-list keys: ${data.keys - guardedAllowList}")
    }

    @Test
    fun `guarded delete sends only the version`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteTaskGuarded("rec1", "v1")
        val delete = server.takeRequest()
        assertEquals("/api/gsd/tasks/rec1", delete.path)
        assertEquals("DELETE", delete.method)
        val body = Json.parseToJsonElement(delete.body.readUtf8()).jsonObject
        assertEquals(setOf("expected_version"), body.keys)
        assertEquals("v1", body["expected_version"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a 412 throws VersionConflict`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(412))
        assertFailsWith<PocketBaseApi.VersionConflict> { api.updateTaskGuarded("rec1", "v1", WireTask(taskId = "t1")) }
        server.enqueue(MockResponse().setResponseCode(412))
        assertFailsWith<PocketBaseApi.VersionConflict> { api.deleteTaskGuarded("rec1", "v1") }
    }

    @Test
    fun `a guarded update accepts an empty 204`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.updateTaskGuarded("rec1", "v1", WireTask(taskId = "t1"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the account route reports a 404 when the hook is not deployed`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val failure = assertFailsWith<PocketBaseApi.HttpError> { api.deleteAccountViaRoute() }
        assertEquals(404, failure.code)
    }

    @Test
    fun `the account route succeeds with 204 and carries the auth header`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        api.deleteAccountViaRoute()
        val recorded = server.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("raw-jwt-token", recorded.getHeader("Authorization"))
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
    fun `confirmIdentity asks auth-refresh and passes when the record is the owner`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"token":"fresh-jwt","record":{"id":"user-1","email":"v@example.com"}}"""))
        api.confirmIdentity("user-1")
        assertEquals(1, server.requestCount)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/collections/users/auth-refresh", recorded.path)
        assertEquals("raw-jwt-token", recorded.getHeader("Authorization"))
    }

    @Test
    fun `confirmIdentity is unauthorized when the server treats the session as a guest`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertFailsWith<PocketBaseApi.Unauthorized> { api.confirmIdentity("user-1") }
    }

    /** A 403 is a dead session too, so the resurrect step stops the run on it, as iOS #97 does (AND-RESURRECT-2). */
    @Test
    fun `confirmIdentity is unauthorized when the server forbids the session`(): Unit = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertFailsWith<PocketBaseApi.Unauthorized> { api.confirmIdentity("user-1") }
    }

    @Test
    fun `confirmIdentity rejects a session that belongs to another account`(): Unit = runBlocking {
        server.enqueue(MockResponse().setBody("""{"token":"fresh-jwt","record":{"id":"someone-else","email":"x@example.com"}}"""))
        assertFailsWith<IdentityMismatch> { api.confirmIdentity("user-1") }
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
