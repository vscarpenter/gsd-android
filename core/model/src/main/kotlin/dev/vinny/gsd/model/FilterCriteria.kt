package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class FilterStatus {
    @SerialName("all") ALL,
    @SerialName("active") ACTIVE,
    @SerialName("completed") COMPLETED,
}

@Serializable
data class DateRange(
    @Serializable(with = InstantIso8601Serializer::class) val start: Instant? = null,
    @Serializable(with = InstantIso8601Serializer::class) val end: Instant? = null,
)

/**
 * The predicate bundle behind smart views, filters, and search (spec §7.9).
 * All present criteria are ANDed. camelCase-codable — shared interchange shape.
 */
@Serializable
data class FilterCriteria(
    val quadrants: List<String> = emptyList(),
    val status: FilterStatus = FilterStatus.ALL,
    val tags: List<String> = emptyList(),
    val dueDateRange: DateRange? = null,
    val overdue: Boolean = false,
    val dueToday: Boolean = false,
    val dueThisWeek: Boolean = false,
    val noDueDate: Boolean = false,
    val recurrence: List<Recurrence> = emptyList(),
    val recentlyAdded: Boolean = false,
    val recentlyCompleted: Boolean = false,
    val readyToWork: Boolean = false,
    val searchQuery: String = "",
) {
    fun matches(task: Task, allTasks: List<Task>, now: Instant, zone: ZoneId): Boolean {
        if (task.isSaved) return false // Saved lives outside active views; allTasks keeps it for blocking
        val today = now.atZone(zone).toLocalDate()
        if (quadrants.isNotEmpty() && task.quadrant.id !in quadrants) return false
        when (status) {
            FilterStatus.ACTIVE -> if (task.completed) return false
            FilterStatus.COMPLETED -> if (!task.completed) return false
            FilterStatus.ALL -> Unit
        }
        if (tags.isNotEmpty() && !tags.all { it in task.tags }) return false
        dueDateRange?.let { range ->
            val due = task.dueDate ?: return false
            if (range.start != null && due.isBefore(range.start)) return false
            if (range.end != null && due.isAfter(range.end)) return false
        }
        val dueDay = task.dueDate?.atZone(zone)?.toLocalDate()
        if (overdue && !(dueDay != null && !task.completed && dueDay.isBefore(today))) return false
        if (dueToday && !(dueDay != null && !task.completed && dueDay == today)) return false
        if (dueThisWeek) {
            val inWindow = dueDay != null && !task.completed &&
                !dueDay.isBefore(today) && dueDay.isBefore(today.plusDays(7))
            if (!inWindow) return false
        }
        if (noDueDate && task.dueDate != null) return false
        if (recurrence.isNotEmpty() && task.recurrence !in recurrence) return false
        val sevenDaysAgo = now.minusSeconds(7L * 86_400)
        if (recentlyAdded && task.createdAt.isBefore(sevenDaysAgo)) return false
        if (recentlyCompleted &&
            !(task.completed && task.completedAt?.isBefore(sevenDaysAgo) == false)
        ) return false
        if (readyToWork && (task.completed || DependencyGraph.isBlocked(allTasks, task))) return false
        val q = searchQuery.trim().lowercase()
        if (q.isNotEmpty()) {
            val haystacks = sequenceOf(task.title, task.description) +
                task.tags.asSequence() + task.subtasks.asSequence().map { it.title }
            if (haystacks.none { q in it.lowercase() }) return false
        }
        return true
    }
}

data class SmartView(
    val id: String,
    val name: String,
    val icon: String,
    val criteria: FilterCriteria,
    val isBuiltIn: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** The nine read-only built-ins with fixed human-readable IDs (spec §8.13). */
object BuiltInSmartViews {
    private val epoch = Instant.EPOCH

    private fun view(id: String, name: String, icon: String, criteria: FilterCriteria) =
        SmartView(id, name, icon, criteria, isBuiltIn = true, createdAt = epoch, updatedAt = epoch)

    val all: List<SmartView> = listOf(
        view("today-focus", "Today's Focus", "adjust",
            FilterCriteria(quadrants = listOf("urgent-important"), status = FilterStatus.ACTIVE)),
        view("this-week", "This Week", "calendar_month",
            FilterCriteria(status = FilterStatus.ACTIVE, dueThisWeek = true)),
        view("overdue", "Overdue Backlog", "warning",
            FilterCriteria(status = FilterStatus.ACTIVE, overdue = true)),
        view("no-deadline", "No Deadline", "event_busy",
            FilterCriteria(status = FilterStatus.ACTIVE, noDueDate = true)),
        view("recently-added", "Recently Added", "auto_awesome",
            FilterCriteria(status = FilterStatus.ACTIVE, recentlyAdded = true)),
        view("weeks-wins", "This Week's Wins", "verified",
            FilterCriteria(status = FilterStatus.COMPLETED, recentlyCompleted = true)),
        view("all-completed", "All Completed", "check_circle",
            FilterCriteria(status = FilterStatus.COMPLETED)),
        view("recurring", "Recurring Tasks", "repeat",
            FilterCriteria(status = FilterStatus.ACTIVE,
                recurrence = listOf(Recurrence.DAILY, Recurrence.WEEKLY, Recurrence.MONTHLY))),
        view("ready-to-work", "Ready to Work", "bolt",
            FilterCriteria(status = FilterStatus.ACTIVE, readyToWork = true)),
    )
}
