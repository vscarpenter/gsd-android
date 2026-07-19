package dev.vinny.gsd.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.vinny.gsd.matrix.TaskRowActions
import dev.vinny.gsd.model.BuiltInSmartViews
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class BrowseTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    @Test
    fun browseContentShowsArchiveSectionsAndCounts() {
        val q1Task = Task(title = "hot", urgent = true, important = true, createdAt = t0, updatedAt = t0)
        val state = buildBrowseState(
            views = BuiltInSmartViews.all,
            pinnedIds = listOf("today-focus"),
            tasks = listOf(q1Task),
            now = t0,
            zone = ZoneOffset.UTC,
        )
        assertEquals(1, state.pinned.size)
        assertEquals(1, state.pinned.single().count)
        assertEquals(8, state.builtIn.size)

        var opened: String? = null
        rule.setContent {
            GsdTheme {
                BrowseContent(
                    state = state,
                    onOpenArchive = {},
                    onOpenView = { opened = it },
                    onNewView = {},
                    onTogglePin = {},
                    onEditView = {},
                    onDeleteView = {},
                )
            }
        }
        rule.onNodeWithText("Archive").assertIsDisplayed()
        rule.onNodeWithText("PINNED").assertIsDisplayed()
        rule.onNodeWithText("Today's Focus").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("New Smart View"))
        rule.onNodeWithText("New Smart View").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Today's Focus"))
        rule.onNodeWithText("Today's Focus").performClick()
        assertEquals("today-focus", opened)
    }

    @Test
    fun viewListShowsViewAwareEmptyState() {
        val overdue = BuiltInSmartViews.all.first { it.id == "overdue" }
        rule.setContent {
            GsdTheme {
                ViewListContent(view = overdue, allTasks = emptyList(), rowActions = { TaskRowActions() })
            }
        }
        rule.onNodeWithText("Nothing overdue").assertIsDisplayed()
    }
}
