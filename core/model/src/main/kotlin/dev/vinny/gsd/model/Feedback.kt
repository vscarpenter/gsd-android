package dev.vinny.gsd.model

import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

enum class Sentiment(val wire: String) {
    UP("up"),
    DOWN("down"),
}

enum class FeedbackCategory(val wire: String) {
    IDEA("idea"),
    PRAISE("praise"),
    GRIPE("gripe"),
    BUG("bug"),
}

data class RoadmapItem(val slug: String, val label: String, val description: String)

object Roadmap {
    val items: List<RoadmapItem> = listOf(
        RoadmapItem("natural-language-dates", "Natural language dates", "Dates like \"next Monday\" or \"in 2 weeks\""),
        RoadmapItem("calendar-sync", "Calendar sync", "Sync tasks with Apple Calendar or Google Calendar"),
        RoadmapItem("task-templates", "Task templates", "Create and reuse templates for common tasks"),
        RoadmapItem("weekly-review", "Weekly review", "Guided weekly review ritual"),
        RoadmapItem("focus-timer", "Focus timer", "Pomodoro-style focus sessions"),
        RoadmapItem("task-notes", "Task notes", "Detailed notes on tasks"),
        RoadmapItem("shared-lists", "Shared lists", "Collaborate on task lists with others"),
        RoadmapItem("wear-os", "Wear OS app", "Your Do First quadrant on your watch"),
    )

    fun isSlug(slug: String): Boolean = items.any { it.slug == slug }
}

data class FeedbackDraft(
    val sentiment: Sentiment? = null,
    val category: FeedbackCategory? = null,
    val message: String = "",
    val votes: List<String> = emptyList(),
    val submissionId: String,
) {
    val isEmpty: Boolean
        get() = sentiment == null && category == null && message.trim().isEmpty() && votes.isEmpty()
}

object FeedbackPayload {
    const val MAX_MESSAGE_LENGTH = 1000

    val FIELDS: List<String> = listOf(
        "submission_id",
        "sentiment",
        "category",
        "message",
        "votes",
        "app_version",
        "client_submitted_at",
    )

    fun build(draft: FeedbackDraft, appVersion: String, submittedAt: Instant): JsonObject =
        buildJsonObject {
            put("submission_id", JsonPrimitive(draft.submissionId))
            put("sentiment", JsonPrimitive(draft.sentiment?.wire ?: ""))
            put("category", JsonPrimitive(draft.category?.wire ?: ""))
            put("message", JsonPrimitive(draft.message.trim().take(MAX_MESSAGE_LENGTH)))
            put("votes", JsonArray(
                draft.votes.distinct()
                    .filter(Roadmap::isSlug)
                    .map(::JsonPrimitive)
            ))
            put("app_version", JsonPrimitive(appVersion.take(20)))
            put("client_submitted_at", JsonPrimitive(Interchange.iso(submittedAt)))
        }
}
