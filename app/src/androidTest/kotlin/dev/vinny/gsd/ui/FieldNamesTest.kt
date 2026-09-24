package dev.vinny.gsd.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.vinny.gsd.capture.CaptureBarContent
import dev.vinny.gsd.capture.CaptureState
import dev.vinny.gsd.palette.CommandPalette
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

/**
 * Review, Accessibility: TalkBack read every text field as an unnamed edit box. Each hint sat beside
 * its field and was read as loose text. Inside the field, the field merges it as its name.
 */
class FieldNamesTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theCaptureFieldCarriesItsHint() {
        rule.setContent {
            GsdTheme {
                CaptureBarContent(state = CaptureState(), onTextChange = {}, onCycleOverride = {}, onSubmit = {}, onDetails = {})
            }
        }
        rule.onNodeWithTag("capture_field").assert(hasText("Capture a task", substring = true))
    }

    @Test
    fun thePaletteQueryCarriesItsHint() {
        rule.setContent {
            GsdTheme {
                CommandPalette(
                    tasks = emptyList(),
                    views = emptyList(),
                    onOpenTask = {},
                    onOpenView = {},
                    onAction = {},
                    onNavigate = {},
                    onDismiss = {},
                )
            }
        }
        rule.onNodeWithTag("palette_query").assert(hasText("Search tasks and commands", substring = true))
    }
}
