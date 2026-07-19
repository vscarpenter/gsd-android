package dev.vinny.gsd.widget

import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
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
data class WidgetSnapshot(val items: List<WidgetItem>, val totalActive: Int) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val dueFormat = DateTimeFormatter.ofPattern("MMM d")

        fun build(tasks: List<Task>, now: Instant, zone: ZoneId): WidgetSnapshot {
            val focus = tasks
                .filter { it.quadrant == Quadrant.DO_FIRST && !it.completed }
                .sortedWith(compareBy(nullsLast()) { it.dueDate })
            return WidgetSnapshot(
                items = focus.take(5).map { task ->
                    WidgetItem(
                        id = task.id,
                        title = task.title,
                        dueLabel = task.dueDate?.let { dueFormat.format(it.atZone(zone)) } ?: "",
                    )
                },
                totalActive = focus.size,
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
