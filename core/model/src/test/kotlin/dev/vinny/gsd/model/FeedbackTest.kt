package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class FeedbackTest {
    private fun draft(vararg votes: String) = FeedbackDraft(
        sentiment = Sentiment.UP, category = FeedbackCategory.IDEA,
        message = "  more focus  ", votes = votes.toList(), submissionId = "sub-1",
    )
    private val at = Instant.parse("2026-08-29T12:00:00.000Z")

    @Test fun `payload contains exactly the seven pinned keys and nothing else`() {
        val payload = FeedbackPayload.build(draft(), appVersion = "android-0.8.0.10", submittedAt = at)
        assertEquals(
            listOf("submission_id", "sentiment", "category", "message", "votes", "app_version", "client_submitted_at"),
            payload.keys.toList(),
        )   // adding a field without updating the disclosure must FAIL here (P§6.1)
    }

    @Test fun `nulls encode as empty strings, message trims, votes dedupe and filter to shipped slugs`() {
        val payload = FeedbackPayload.build(
            FeedbackDraft(message = "hi", votes = listOf("wear-os", "wear-os", "ios-widgets", "bogus"), submissionId = "s"),
            "android-0.8.0.10", at,
        )
        assertEquals("", payload.getValue("sentiment").jsonPrimitive.content)
        assertEquals("", payload.getValue("category").jsonPrimitive.content)
        assertEquals(listOf("wear-os"), payload.getValue("votes").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test fun `app version clamps to 20 chars`() {
        val payload = FeedbackPayload.build(draft(), "android-0.8.0-rc.1.veryverylong", at)
        assertEquals(20, payload.getValue("app_version").jsonPrimitive.content.length)
    }

    @Test fun `android ships the seven shared slugs plus wear-os`() {
        assertEquals(
            listOf("natural-language-dates", "calendar-sync", "task-templates", "weekly-review",
                   "focus-timer", "task-notes", "shared-lists", "wear-os"),
            Roadmap.items.map { it.slug },
        )   // per-client curation, P§6.1: never reuse a retired slug (ios-widgets, apple-watch are other clients')
    }

    @Test fun `draft emptiness gates send`() {
        assertTrue(FeedbackDraft(submissionId = "s").isEmpty)
        assertTrue(FeedbackDraft(message = "   ", submissionId = "s").isEmpty)
        assertFalse(FeedbackDraft(votes = listOf("wear-os"), submissionId = "s").isEmpty)
    }
}
