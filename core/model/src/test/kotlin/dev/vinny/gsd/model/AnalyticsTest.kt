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
}
