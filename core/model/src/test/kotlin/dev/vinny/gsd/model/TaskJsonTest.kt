package dev.vinny.gsd.model

import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskJsonTest {
    private val t0 = Instant.parse("2026-06-15T08:30:00.500Z")
    private val task = Task(
        id = "abcdefghijklmnopqrstu", title = "Ship", urgent = true,
        important = true, createdAt = t0, updatedAt = t0,
    )

    @Test
    fun `dates encode as iso-8601 utc with milliseconds`() {
        assertTrue("2026-06-15T08:30:00.500Z" in TaskJson.encode(task))
    }

    @Test
    fun `quadrant is never encoded`() {
        val obj = Json.parseToJsonElement(TaskJson.encode(task)).jsonObject
        assertNull(obj["quadrant"])
    }

    @Test
    fun `null optionals are omitted, empties are kept`() {
        val obj = Json.parseToJsonElement(TaskJson.encode(task)).jsonObject
        assertNull(obj["dueDate"])
        assertNull(obj["completedAt"])
        assertEquals("\"\"", obj["description"].toString())
        assertEquals("[]", obj["tags"].toString())
    }

    @Test
    fun `lenient decode fills defaults from required-only record`() {
        val raw = Json.parseToJsonElement(
            """{"id":"abcd","title":"t","urgent":false,"important":true,
                "createdAt":"2026-06-15T08:30:00.500Z","updatedAt":"2026-06-15T08:30:00.500Z"}""",
        )
        val t = TaskJson.decodeOrNull(raw)!!
        assertEquals("", t.description)
        assertEquals(false, t.completed)
        assertEquals(Recurrence.NONE, t.recurrence)
        assertEquals(true, t.notificationEnabled)
    }

    @Test
    fun `unknown keys are ignored`() {
        val raw = Json.parseToJsonElement(
            """{"id":"abcd","title":"t","urgent":false,"important":false,
                "createdAt":"2026-06-15T08:30:00.500Z","updatedAt":"2026-06-15T08:30:00.500Z",
                "vectorClock":{"a":1}}""",
        )
        assertEquals("t", TaskJson.decodeOrNull(raw)!!.title)
    }

    @Test
    fun `missing required field yields null, not an exception`() {
        assertNull(TaskJson.decodeOrNull(Json.parseToJsonElement("""{"title":"no id"}""")))
    }

    @Test
    fun `round-trip preserves everything including parentTaskId`() {
        val full = task.copy(
            dueDate = t0, tags = listOf("work"), parentTaskId = "parentid1234",
            subtasks = listOf(Subtask("subid", "s", true)),
            timeEntries = listOf(TimeEntry("abcd1234", t0, t0.plusSeconds(120), "note")),
        )
        assertEquals(full, TaskJson.decodeOrNull(Json.parseToJsonElement(TaskJson.encode(full))))
    }

    @Test
    fun `offset timestamps decode to the same instant`() {
        val raw = Json.parseToJsonElement(
            """{"id":"abcd","title":"t","urgent":false,"important":false,
                "createdAt":"2026-07-19T14:03:22.114-05:00","updatedAt":"2026-06-15T08:30:00.500Z"}""",
        )
        assertEquals(Instant.parse("2026-07-19T19:03:22.114Z"), TaskJson.decodeOrNull(raw)!!.createdAt)
    }
}
