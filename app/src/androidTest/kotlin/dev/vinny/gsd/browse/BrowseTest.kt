package dev.vinny.gsd.browse

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import dev.vinny.gsd.matrix.TaskRowActions
import dev.vinny.gsd.model.BuiltInSmartViews
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
                    trashCount = 0,
                    onOpenTrash = {},
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
        // Review, Copy: a view's menu button announced "Task options"
        rule.onNodeWithContentDescription("Today's Focus options").assertIsDisplayed()
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
        rule.onNodeWithText("You're all caught up.").assertIsDisplayed() // finding 32: headline plus sentence
    }

    /** Review finding 32: a custom view's empty state read only "Nothing here". */
    @Test
    fun aCustomViewsEmptyStateNamesTheView() {
        val custom = BuiltInSmartViews.all.first { it.id == "today-focus" }.copy(id = "custom-errands", name = "Errands")
        rule.setContent {
            GsdTheme {
                ViewListContent(view = custom, allTasks = emptyList(), rowActions = { TaskRowActions() })
            }
        }
        rule.onNodeWithText("Nothing here yet").assertIsDisplayed()
        rule.onNodeWithText("Tasks matching \"Errands\" will appear here.").assertIsDisplayed()
    }

    @Test
    fun viewListShowsSkeletonWhileTasksNotLoaded() {
        val overdue = BuiltInSmartViews.all.first { it.id == "overdue" }
        rule.setContent {
            GsdTheme {
                ViewListContent(view = overdue, allTasks = null, rowActions = { TaskRowActions() })
            }
        }
        rule.onNodeWithContentDescription("Loading").assertIsDisplayed()
        rule.onNodeWithText("Nothing overdue").assertDoesNotExist()
    }

    /** Review finding 22: the search field read as a label, stood 37 dp tall, and had no clear button. */
    @Test
    fun theSearchFieldIsATallFieldWithAClearButton() {
        content(buildBrowseState(BuiltInSmartViews.all, emptyList(), emptyList(), t0, ZoneOffset.UTC), emptyList())
        rule.onNodeWithTag("browse_search").assertHeightIsAtLeast(48.dp)

        rule.onNodeWithTag("browse_search").performTextInput("milk")
        rule.onNodeWithText("Archive").assertDoesNotExist()
        rule.onNodeWithContentDescription("Clear search").performClick()

        rule.onNodeWithText("Archive").assertIsDisplayed()
    }

    private fun content(state: BrowseState, tasks: List<Task>, onOpen: (String) -> Unit = {}) = rule.setContent {
        GsdTheme {
            BrowseContent(
                state = state, onOpenArchive = {}, trashCount = 0, onOpenTrash = {}, onOpenView = {}, onNewView = {},
                onTogglePin = {}, onEditView = {}, onDeleteView = {}, tasks = tasks,
                rowActions = { task -> TaskRowActions(onEdit = { onOpen(task.id) }) },
            )
        }
    }

    @Test
    fun oneCharacterQueryReplacesTheListWithActiveMatchesInSharedOrder() {
        val undated = Task(title = "Zebra", urgent = true, important = true, createdAt = t0, updatedAt = t0)
        val dated = Task(title = "Zip", urgent = false, important = true, createdAt = t0.minusSeconds(60), updatedAt = t0, dueDate = t0.plusSeconds(3_600))
        val done = Task(title = "Zed", urgent = true, important = true, completed = true, completedAt = t0, createdAt = t0, updatedAt = t0)
        val bySubtask = Task(title = "Plan", urgent = false, important = false, createdAt = t0.minusSeconds(120), updatedAt = t0,
            subtasks = listOf(Subtask(id = "s1", title = "buy zucchini")))
        val state = buildBrowseState(BuiltInSmartViews.all, emptyList(), listOf(undated, dated, done, bySubtask), t0, ZoneOffset.UTC)
        content(state, listOf(undated, dated, done, bySubtask))
        rule.onNodeWithTag("browse_search").performTextInput(" z ")
        rule.onNodeWithText("Archive").assertDoesNotExist()
        rule.onNodeWithText("Zed").assertDoesNotExist()                     // completed never appears
        rule.onNodeWithText("Zip").assertIsDisplayed()
        rule.onNodeWithText("Zebra").assertIsDisplayed()
        rule.onNodeWithText("Plan").assertIsDisplayed()
        assertTrue(rule.onNodeWithText("Zip").getBoundsInRoot().top < rule.onNodeWithText("Zebra").getBoundsInRoot().top)
        assertTrue(rule.onNodeWithText("Zebra").getBoundsInRoot().top < rule.onNodeWithText("Plan").getBoundsInRoot().top)
    }

    @Test
    fun searchMissShowsTheSharedEmptyCopy() {
        content(buildBrowseState(BuiltInSmartViews.all, emptyList(), emptyList(), t0, ZoneOffset.UTC), emptyList())
        rule.onNodeWithTag("browse_search").performTextInput("qq")
        rule.onNodeWithText("No tasks match \"qq\"").assertIsDisplayed()
        rule.onNodeWithText("Try a different word, tag, or quadrant.").assertIsDisplayed()
    }

    @Test
    fun whitespaceOnlyQueryKeepsTheList() {
        content(buildBrowseState(BuiltInSmartViews.all, emptyList(), emptyList(), t0, ZoneOffset.UTC), emptyList())
        rule.onNodeWithTag("browse_search").performTextInput("   ")
        rule.onNodeWithText("Archive").assertIsDisplayed()
    }
}
