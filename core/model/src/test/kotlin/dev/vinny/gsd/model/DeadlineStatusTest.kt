package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class DeadlineStatusTest {
    private val zone: ZoneId = ZoneId.of("America/Chicago")
    private fun june(day: Int, hour: Int): Instant =
        LocalDateTime.of(2026, 6, day, hour, 0).atZone(zone).toInstant()

    @Test
    fun `due yesterday is overdue`() =
        assertEquals(DeadlineStatus.OVERDUE, DeadlineStatus.of(june(2, 12), june(3, 9), zone))

    @Test
    fun `due earlier today is today, not overdue`() =
        assertEquals(DeadlineStatus.TODAY, DeadlineStatus.of(june(3, 0), june(3, 9), zone))

    @Test
    fun `due later today is today`() =
        assertEquals(DeadlineStatus.TODAY, DeadlineStatus.of(june(3, 23), june(3, 9), zone))

    @Test
    fun `due tomorrow is upcoming even at 23h`() =
        assertEquals(DeadlineStatus.UPCOMING, DeadlineStatus.of(june(4, 0), june(3, 23), zone))
}
