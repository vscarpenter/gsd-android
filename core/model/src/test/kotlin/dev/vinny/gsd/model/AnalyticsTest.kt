package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsTest {
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(
        completed: Boolean = false,
        completedAt: Instant? = null,
        createdAt: Instant = now.minusSeconds(3_600),
        urgent: Boolean = false,
        important: Boolean = false,
        tags: List<String> = emptyList(),
        dueDate: Instant? = null,
        timeEntries: List<TimeEntry> = emptyList(),
    ) = Task(
        title = "t", urgent = urgent, important = important, completed = completed,
        completedAt = completedAt, createdAt = createdAt, updatedAt = createdAt,
        tags = tags, dueDate = dueDate, timeEntries = timeEntries,
    )

    @Test
    fun `stat grid counts and completion rate`() {
        val stats = Analytics.compute(
            listOf(task(), task(), task(completed = true, completedAt = now)),
            windowDays = 7, now = now, zone = zone,
        )
        assertEquals(2, stats.active)
        assertEquals(1, stats.completed)
        assertEquals(1f / 3f, stats.completionRate)
    }

    @Test
    fun `trend buckets created and completed per day`() {
        val yesterday = now.minusSeconds(86_400)
        val stats = Analytics.compute(
            listOf(
                task(createdAt = yesterday),
                task(createdAt = now.minusSeconds(60)),
                task(completed = true, completedAt = yesterday, createdAt = yesterday),
            ),
            windowDays = 7, now = now, zone = zone,
        )
        assertEquals(7, stats.trend.size)
        val yesterdayPoint = stats.trend[5]
        assertEquals(2, yesterdayPoint.created)
        assertEquals(1, yesterdayPoint.completed)
        val todayPoint = stats.trend[6]
        assertEquals(1, todayPoint.created)
    }

    @Test
    fun `quadrant distribution, top tags, minutes by quadrant`() {
        val stats = Analytics.compute(
            listOf(
                task(urgent = true, important = true, tags = listOf("work"),
                    timeEntries = listOf(TimeEntry("abcd1234", now.minusSeconds(600), now))),
                task(tags = listOf("work", "home")),
            ),
            windowDays = 7, now = now, zone = zone,
        )
        assertEquals(1, stats.activeByQuadrant[Quadrant.DO_FIRST])
        assertEquals(1, stats.activeByQuadrant[Quadrant.ELIMINATE])
        assertEquals("work" to 2, stats.topTags.first())
        assertEquals(10, stats.minutesByQuadrant[Quadrant.DO_FIRST])
        assertEquals(10, stats.trackedTotalMinutes)
    }

    @Test
    fun `upcoming deadlines sorted, overdue counted`() {
        val stats = Analytics.compute(
            listOf(
                task(dueDate = now.plusSeconds(200_000)),
                task(dueDate = now.plusSeconds(100_000)),
                task(dueDate = now.minusSeconds(200_000)),
            ),
            windowDays = 7, now = now, zone = zone,
        )
        assertEquals(1, stats.overdueCount)
        assertEquals(2, stats.upcoming.size)
        assertEquals(now.plusSeconds(100_000), stats.upcoming.first().dueDate)
    }

    @Test
    fun `quadrant stats are always four, Q1 to Q4, completed over total, all time`() {
        val stats = Analytics.compute(
            listOf(
                task(urgent = true, important = true),
                task(urgent = true, important = true, completed = true, completedAt = now),
                task(urgent = false, important = true, completed = true, completedAt = now.minusSeconds(40L * 86_400)),
            ),
            windowDays = 7, now = now, zone = zone,
        )
        assertEquals(Quadrant.entries.toList(), stats.quadrantStats.map { it.quadrant })
        val q1 = stats.quadrantStats[0]
        assertEquals(2, q1.total); assertEquals(1, q1.completed); assertEquals(1, q1.active)
        assertEquals(0.5f, q1.completionRate)
        val q2 = stats.quadrantStats[1]
        assertEquals(1f, q2.completionRate) // a 40-day-old completion still counts: no window
        assertEquals(0f, stats.quadrantStats[3].completionRate) // empty quadrant → 0, not NaN
    }

    @Test
    fun `percent rounds half up and never truncates`() {
        assertEquals("68%", Analytics.percent(0.675f))
        assertEquals("67%", Analytics.percent(2f / 3f))
        assertEquals("0%", Analytics.percent(0f))
        assertEquals("100%", Analytics.percent(1f))
    }

    @Test
    fun `upcoming is day-based, includes a task due earlier today, and tie-breaks newest first`() {
        val zone = java.time.ZoneId.of("America/Chicago")
        val nine = java.time.LocalDateTime.of(2026, 6, 3, 9, 0).atZone(zone).toInstant()
        val midnight = java.time.LocalDateTime.of(2026, 6, 3, 0, 0).atZone(zone).toInstant()
        val tomorrow = java.time.LocalDateTime.of(2026, 6, 4, 12, 0).atZone(zone).toInstant()
        val yesterday = java.time.LocalDateTime.of(2026, 6, 2, 12, 0).atZone(zone).toInstant()
        val older = Task(title = "older", urgent = false, important = false, createdAt = nine.minusSeconds(600), updatedAt = nine, dueDate = tomorrow)
        val newer = Task(title = "newer", urgent = false, important = false, createdAt = nine.minusSeconds(60), updatedAt = nine, dueDate = tomorrow)
        val earlierToday = Task(title = "today", urgent = false, important = false, createdAt = nine, updatedAt = nine, dueDate = midnight)
        val late = Task(title = "late", urgent = false, important = false, createdAt = nine, updatedAt = nine, dueDate = yesterday)
        val stats = Analytics.compute(listOf(older, newer, earlierToday, late), 7, nine, zone)
        assertEquals(listOf("today", "newer", "older"), stats.upcoming.map { it.title })
        assertEquals(1, stats.overdueCount)
    }
}
