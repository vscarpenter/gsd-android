package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class TrendPoint(val day: LocalDate, val created: Int, val completed: Int)

/** Per-quadrant counts over the active table, all-time (S§2.1). `total` = active + completed. */
data class QuadrantStat(val quadrant: Quadrant, val total: Int, val completed: Int) {
    val active: Int get() = total - completed
    val completionRate: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

data class DashboardStats(
    val active: Int,
    val completed: Int,
    val completionRate: Float,
    val trackedTotalMinutes: Int,
    val trend: List<TrendPoint>,
    val activeByQuadrant: Map<Quadrant, Int>,
    val topTags: List<Pair<String, Int>>,
    val minutesByQuadrant: Map<Quadrant, Int>,
    val upcoming: List<Task>,
    val overdueCount: Int,
    val quadrantStats: List<QuadrantStat>,
)

/** The §8.15 metric set, computed from the current task set. No streaks — ever. */
object Analytics {
    fun compute(tasks: List<Task>, windowDays: Int, now: Instant, zone: ZoneId): DashboardStats {
        val today = now.atZone(zone).toLocalDate()
        val active = tasks.count { !it.completed }
        val completed = tasks.count { it.completed }
        val days = (windowDays - 1) downTo 0
        val trend = days.map { back ->
            val day = today.minusDays(back.toLong())
            TrendPoint(
                day = day,
                created = tasks.count { it.createdAt.atZone(zone).toLocalDate() == day },
                completed = tasks.count { it.completedAt?.atZone(zone)?.toLocalDate() == day },
            )
        }
        val activeTasks = tasks.filter { !it.completed }
        val upcoming = activeTasks
            .filter { task -> task.dueDate?.let { !it.atZone(zone).toLocalDate().isBefore(today) } == true }
            .sortedWith(compareBy<Task> { it.dueDate }.thenByDescending { it.createdAt })
            .take(5)
        return DashboardStats(
            active = active,
            completed = completed,
            completionRate = if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size,
            trackedTotalMinutes = tasks.sumOf { it.timeSpent },
            trend = trend,
            activeByQuadrant = Quadrant.entries.associateWith { quadrant ->
                activeTasks.count { it.quadrant == quadrant }
            },
            topTags = tasks.flatMap { it.tags }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value },
            minutesByQuadrant = Quadrant.entries.associateWith { quadrant ->
                tasks.filter { it.quadrant == quadrant }.sumOf { it.timeSpent }
            },
            upcoming = upcoming,
            overdueCount = activeTasks.count {
                it.dueDate?.atZone(zone)?.toLocalDate()?.isBefore(today) == true
            },
            quadrantStats = Quadrant.entries.map { quadrant ->
                val inQuadrant = tasks.filter { it.quadrant == quadrant }
                QuadrantStat(quadrant, total = inQuadrant.size, completed = inQuadrant.count { it.completed })
            },
        )
    }

    /** "68%" — round half up; siblings round, they never truncate (S§2.1). */
    fun percent(rate: Float): String = "${(rate * 100f).roundToInt()}%"
}
