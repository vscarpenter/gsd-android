package dev.vinny.gsd.widget

import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TopThree
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WidgetItem(val id: String, val title: String, val dueLabel: String)

/**
 * The precomputed widget payload (spec §12.1): the widget reads this snapshot,
 * never the database. Q1-active tasks, dated ones first.
 */
@Serializable
data class WidgetSnapshot(
    val items: List<WidgetItem>,
    val totalActive: Int,
    /** The local date [focusTasks] were picked for, or null when nothing is picked (spec-top-three.md §9). */
    val focusDate: String? = null,
    /** Today's picks in rank order, at most three. Only drawn while [focusDate] is the local today. */
    val focusTasks: List<WidgetItem> = emptyList(),
) {
    /**
     * What the widget draws, decided when it draws: the picks while their date is [today], otherwise the
     * Do First list. The widget never redraws itself at midnight, so a stored snapshot can outlive its day.
     */
    fun visibleItems(today: String): List<WidgetItem> =
        if (focusDate == today && focusTasks.isNotEmpty()) focusTasks else items

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val dueFormat = DateTimeFormatter.ofPattern("MMM d")

        fun build(tasks: List<Task>, now: Instant, zone: ZoneId): WidgetSnapshot {
            fun item(task: Task) = WidgetItem(
                id = task.id,
                title = task.title,
                dueLabel = task.dueDate?.let { dueFormat.format(it.atZone(zone)) } ?: "",
            )
            val byId = tasks.associateBy { it.id }
            val picks = TopThree.todaysPicks(tasks, now, zone).picks.mapNotNull(byId::get)
            val focus = tasks
                .filter { it.quadrant == Quadrant.DO_FIRST && !it.completed && !it.isSaved }
                .sortedWith(compareBy(nullsLast()) { it.dueDate })
            return WidgetSnapshot(
                items = focus.take(5).map(::item),
                totalActive = focus.size,
                focusDate = TopThree.today(now, zone).takeIf { picks.isNotEmpty() },
                focusTasks = picks.map(::item),
            )
        }

        fun encode(snapshot: WidgetSnapshot): String = json.encodeToString(serializer(), snapshot)

        fun decode(raw: String?): WidgetSnapshot =
            raw?.let {
                try {
                    json.decodeFromString(serializer(), it)
                } catch (_: Exception) {
                    null
                }
            } ?: WidgetSnapshot(emptyList(), 0)
    }
}
