package dev.vinny.gsd.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.vinny.gsd.ComposeDatabaseRule
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.NotificationSettingsStore
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.theme.GsdTheme
import java.time.Clock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Review finding 25: Save sat at the end of an eleven-section scroll, and a swipe down threw edits
 * away with no prompt. Cancel and Save now sit in a pinned header, and leaving an edited draft asks.
 */
class EditorDiscardTest {
    private val database = ComposeDatabaseRule()
    private val rule = createComposeRule()

    /** The database closes only after the Compose rule disposes the content (AND-TEARDOWN-RACE). */
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(database).around(rule)

    private lateinit var db: GsdDatabase
    private lateinit var viewModel: TaskEditorViewModel
    private lateinit var reminders: NotificationSettingsStore

    @Before
    fun setUp() {
        db = database.db
        viewModel = database.track(TaskEditorViewModel(TaskStore(db, Clock.systemUTC())))
        reminders = NotificationSettingsStore(db, Clock.systemUTC())
        viewModel.openNew(null)
    }

    @Test
    fun cancelWithAnEditAsksBeforeDiscarding() {
        rule.setContent { GsdTheme { TaskEditorSheet(viewModel, reminders, onDismiss = {}) } }
        rule.onNodeWithTag("editor_title").performTextInput("half-typed")

        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        rule.onNodeWithText("Keep editing").performClick()
        assertEquals("half-typed", viewModel.draft.value?.title)

        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Discard").performClick()
        rule.waitForIdle()
        assertNull(viewModel.draft.value)
    }

    @Test
    fun cancelWithNoEditsClosesAtOnce() {
        rule.setContent { GsdTheme { TaskEditorSheet(viewModel, reminders, onDismiss = {}) } }

        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertNull(viewModel.draft.value)
    }

    @Test
    fun saveIsOnScreenWithoutScrolling() {
        rule.setContent { GsdTheme { TaskEditorSheet(viewModel, reminders, onDismiss = {}) } }

        rule.onNodeWithText("Save").assertIsDisplayed()
    }
}
