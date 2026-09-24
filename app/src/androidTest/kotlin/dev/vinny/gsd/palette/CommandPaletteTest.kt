package dev.vinny.gsd.palette

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.vinny.gsd.model.BuiltInSmartViews
import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

class CommandPaletteTest {
    @get:Rule
    val rule = createComposeRule()

    private var views by mutableStateOf(emptyList<SmartView>())

    private fun palette() = rule.setContent {
        GsdTheme {
            CommandPalette(
                tasks = emptyList(),
                views = views,
                onOpenTask = {},
                onOpenView = {},
                onAction = {},
                onNavigate = {},
                onDismiss = {},
            )
        }
    }

    /**
     * Review finding 26: the smart views arrive a frame after the palette opens. A keyed section
     * header held the scroll where the first section had been, so the list opened past them.
     */
    @Test
    fun thePaletteOpensAtTheTopWhenTheViewsArriveLate() {
        palette()
        rule.waitForIdle()

        views = BuiltInSmartViews.all
        rule.waitForIdle()

        rule.onNodeWithText("Smart Views").assertIsDisplayed() // a heading, read as written
        rule.onNodeWithText(BuiltInSmartViews.all.first().name).assertIsDisplayed()
    }

    /** Review finding 26: the palette never focused its query field, so typing did nothing at first. */
    @Test
    fun thePaletteFocusesItsQuery() {
        palette()

        rule.onNodeWithTag("palette_query").assertIsFocused()
    }
}
