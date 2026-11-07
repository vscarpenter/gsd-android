package dev.vinny.gsd.matrix

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TodayView
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** The Today section's copy, accessible names, and offers (spec-top-three.md §4). */
class TodaySectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-11-04T16:00:00Z") // 2026-11-04, Wednesday
    private val today = LocalDate.of(2026, 11, 4)
    private val t0 = Instant.parse("2026-10-01T00:00:00Z")

    private fun task(id: String, date: String? = null, rank: Int? = null) = Task(
        id = id, title = "Task $id", urgent = true, important = true,
        createdAt = t0, updatedAt = t0, focusDate = date, focusRank = rank,
    )

    private fun show(
        tasks: List<Task>,
        suggestionsOpen: Boolean = false,
        onToggle: () -> Unit = {},
        onAdd: (Task) -> Unit = {},
        onTidy: () -> Unit = {},
        onNotNow: () -> Unit = {},
        tidyDismissed: Boolean = false,
    ) = rule.setContent {
        GsdTheme {
            TodaySection(
                view = TodayView.build(tasks, now, windowDays = 2, zone),
                suggestionsOpen = suggestionsOpen,
                tidyDismissed = tidyDismissed,
                today = today,
                onToggleSuggestions = onToggle,
                onAdd = onAdd,
                onTidy = onTidy,
                onNotNow = onNotNow,
                rowActions = { TaskRowActions() },
            )
        }
    }

    @Test
    fun noPicksIsOneQuietLine() {
        show(listOf(task("a")))
        rule.onNodeWithText("Today").assertIsDisplayed()
        rule.onNodeWithText("Pick up to three for today").assertIsDisplayed()
        rule.onNodeWithText("Add to today").assertDoesNotExist()
    }

    @Test
    fun theLineOpensTheSuggestions() {
        var toggled = 0
        show(listOf(task("a")), onToggle = { toggled++ })
        rule.onNodeWithTag("today_pick_line").performClick()
        assertEquals(1, toggled)
    }

    @Test
    fun openSuggestionsOfferAddToToday() {
        val added = mutableListOf<String>()
        show(listOf(task("a")), suggestionsOpen = true, onAdd = { added += it.id })
        rule.onNode(hasContentDescription("Add to today: Task a")).performClick()
        assertEquals(listOf("a"), added)
    }

    @Test
    fun noSuggestionsSaysSo() {
        show(emptyList(), suggestionsOpen = true)
        rule.onNodeWithText("Nothing to suggest right now.").assertIsDisplayed()
    }

    @Test
    fun theSectionIsReadAsTodayTwoOfThree() {
        show(listOf(task("a", "2026-11-04", 1), task("b", "2026-11-04", 2)))
        rule.onNode(hasContentDescription("Today, 2 of 3")).assertIsDisplayed()
    }

    @Test
    fun rowsAreReadAsFirstAndSecond() {
        show(listOf(task("a", "2026-11-04", 1), task("b", "2026-11-04", 2)))
        rule.onAllNodes(stateIs("first")).assertCountEquals(1)
        rule.onAllNodes(stateIs("second")).assertCountEquals(1)
        rule.onAllNodes(stateIs("third")).assertCountEquals(0)
    }

    @Test
    fun yesterdaysPicksAreOfferedAsFromYesterday() {
        show(listOf(task("a", "2026-11-03", 1)))
        rule.onNodeWithText("From yesterday").assertIsDisplayed()
        rule.onNodeWithText("Task a").assertIsDisplayed()
    }

    @Test
    fun anEarlierDayIsNamedByWeekday() {
        show(listOf(task("a", "2026-10-30", 1))) // a Friday
        rule.onNodeWithText("From Friday").assertIsDisplayed()
    }

    @Test
    fun aConflictOffersTidyAndNotNowWritesNothing() {
        var tidied = 0
        var notNow = 0
        show(
            listOf(task("a", "2026-11-04", 1), task("b", "2026-11-04", 1)),
            onTidy = { tidied++ },
            onNotNow = { notNow++ },
        )
        rule.onNodeWithText("Two devices picked for today. Tidy up?").assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        assertEquals(0, tidied)
        assertEquals(1, notNow)
        rule.onNodeWithText("Tidy").performClick()
        assertEquals(1, tidied)
    }

    @Test
    fun aDismissedTidyOfferStaysAway() {
        show(listOf(task("a", "2026-11-04", 1), task("b", "2026-11-04", 1)), tidyDismissed = true)
        rule.onNodeWithTag("today_tidy").assertDoesNotExist()
    }

    @Test
    fun aFullDayOffersNothingMore() {
        show(
            listOf(
                task("a", "2026-11-04", 1), task("b", "2026-11-04", 2), task("c", "2026-11-04", 3),
                task("old", "2026-11-03", 1), task("q1"),
            ),
            suggestionsOpen = true,
        )
        rule.onNodeWithTag("today_pick_line").assertDoesNotExist()
        rule.onNodeWithText("From yesterday").assertDoesNotExist()
        rule.onAllNodesWithText("Add to today").assertCountEquals(0)
    }

    @Test
    fun copyNeverShamesTheUser() {
        show(
            listOf(task("a", "2026-11-04", 1), task("b", "2026-11-04", 1), task("old", "2026-11-03", 2)),
            suggestionsOpen = true,
        )
        listOf("missed", "unfinished", "failed", "overdue from yesterday", "behind").forEach { word ->
            rule.onAllNodes(hasText(word, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }

    private fun stateIs(label: String) = SemanticsMatcher("state description is $label") {
        it.config.getOrNull(SemanticsProperties.StateDescription) == label
    }
}
