package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TimeTrackingTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(entries: List<TimeEntry> = emptyList()) =
        Task(title = "t", urgent = false, important = false, createdAt = t0, updatedAt = t0,
            timeEntries = entries)

    @Test
    fun `start creates a running entry with an 8-char id`() {
        val started = TimeTracking.start(task(), t0)
        val entry = started.timeEntries.single()
        assertEquals(8, entry.id.length)
        assertEquals(t0, entry.startedAt)
        assertNull(entry.endedAt)
    }

    @Test
    fun `second start is rejected`() {
        val running = TimeTracking.start(task(), t0)
        assertFailsWith<TimeTracking.TimerAlreadyRunning> { TimeTracking.start(running, t0.plusSeconds(60)) }
    }

    @Test
    fun `stop closes the running entry and timeSpent floors minutes`() {
        val stopped = TimeTracking.stop(TimeTracking.start(task(), t0), t0.plusSeconds(150))
        assertEquals(t0.plusSeconds(150), stopped.timeEntries.single().endedAt)
        assertEquals(2, stopped.timeSpent)
        assertNull(TimeTracking.runningEntry(stopped))
    }

    @Test
    fun `stop without a running entry is rejected`() {
        assertFailsWith<TimeTracking.NoRunningTimer> { TimeTracking.stop(task(), t0) }
    }

    @Test
    fun `duration formatting`() {
        assertEquals("< 1m", TimeTracking.formatMinutes(0))
        assertEquals("45m", TimeTracking.formatMinutes(45))
        assertEquals("2h 5m", TimeTracking.formatMinutes(125))
        assertEquals("2h", TimeTracking.formatMinutes(120))
    }
}
