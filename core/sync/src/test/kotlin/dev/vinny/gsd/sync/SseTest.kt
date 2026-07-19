package dev.vinny.gsd.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SseTest {

    private fun feed(parser: SseParser, raw: String): List<SseEvent> =
        raw.lines().flatMap { parser.feedLine(it) }

    @Test
    fun `frames parse with heartbeats, no-space colons, and multi-line data`() {
        val parser = SseParser()
        val events = feed(
            parser,
            """
            |id:abc123
            |event:PB_CONNECT
            |data:{"clientId":"client-1"}
            |
            |: heartbeat comment
            |event: tasks
            |data: {"record":{"task_id":"t1"},
            |data: "action":"create"}
            |
            """.trimMargin(),
        )
        assertEquals(2, events.size)
        assertEquals("PB_CONNECT", events[0].event)
        assertEquals("""{"clientId":"client-1"}""", events[0].data)
        assertEquals("tasks", events[1].event)
        assertTrue(events[1].data.contains("\n")) // multi-line data joined with newline
    }

    @Test
    fun `pb_connect client id extraction`() {
        assertEquals("client-1", SseParser.clientId(SseEvent(null, "PB_CONNECT", """{"clientId":"client-1"}""")))
        assertNull(SseParser.clientId(SseEvent(null, "PB_CONNECT", """{"nope":true}""")))
    }

    @Test
    fun `owner fail-closed drops foreign and keeps own or empty`() {
        val record = wire("t1", owner = "someone-else", deviceId = "other-device")
        assertTrue(
            SseApplyRules.decide("create", record, ownUserId = "me", ownDeviceId = "this-device",
                pendingDeleteTaskIds = emptySet(), pendingAnyTaskIds = emptySet())
                is SseDecision.Drop,
        )
        val own = wire("t1", owner = "me", deviceId = "other-device")
        assertTrue(
            SseApplyRules.decide("create", own, "me", "this-device", emptySet(), emptySet())
                is SseDecision.ApplyUpsert,
        )
        val emptyOwner = wire("t1", owner = "", deviceId = "other-device")
        assertTrue(
            SseApplyRules.decide("update", emptyOwner, "me", "this-device", emptySet(), emptySet())
                is SseDecision.ApplyUpsert,
        )
    }

    @Test
    fun `create-update echo filter and pending-delete protection`() {
        val echo = wire("t1", owner = "me", deviceId = "this-device")
        assertTrue(
            SseApplyRules.decide("update", echo, "me", "this-device", emptySet(), emptySet())
                is SseDecision.Drop,
        )
        val foreign = wire("t1", owner = "me", deviceId = "other")
        assertTrue(
            SseApplyRules.decide("update", foreign, "me", "this-device",
                pendingDeleteTaskIds = setOf("t1"), pendingAnyTaskIds = setOf("t1"))
                is SseDecision.Drop, // don't resurrect a locally-deleted task
        )
    }

    @Test
    fun `deletes are not echo-filtered but any pending queue item protects`() {
        val deletion = wire("t1", owner = "me", deviceId = "this-device") // own device id on deletes is fine
        assertTrue(
            SseApplyRules.decide("delete", deletion, "me", "this-device", emptySet(), emptySet())
                is SseDecision.ApplyDelete,
        )
        assertTrue(
            SseApplyRules.decide("delete", deletion, "me", "this-device",
                pendingDeleteTaskIds = emptySet(), pendingAnyTaskIds = setOf("t1"))
                is SseDecision.Drop,
        )
    }

    private fun wire(taskId: String, owner: String, deviceId: String) =
        WireTask(taskId = taskId, owner = owner, deviceId = deviceId,
            clientUpdatedAt = "2026-06-10T12:00:00.000Z")
}
