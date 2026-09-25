package dev.vinny.gsd.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class CaptureBarTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun chipRestsAsAutoThenShowsLiveQuadrant() {
        rule.setContent {
            var state by androidx.compose.runtime.remember { mutableStateOf(CaptureState()) }
            GsdTheme {
                CaptureBarContent(
                    state = state,
                    onTextChange = { state = state.copy(text = it) },
                    onCycleOverride = {},
                    onSubmit = {},
                    onDetails = {},
                )
            }
        }
        rule.onNodeWithText("Auto").assertIsDisplayed()
        rule.onNodeWithTag("capture_field").performTextInput("Ship it !!")
        rule.onNodeWithText("Do First").assertIsDisplayed()
    }

    @Test
    fun tagChipsRenderBeneathField() {
        rule.setContent {
            GsdTheme {
                CaptureBarContent(
                    state = CaptureState(text = "Fix #auth #web"),
                    onTextChange = {},
                    onCycleOverride = {},
                    onSubmit = {},
                    onDetails = {},
                )
            }
        }
        rule.onNodeWithText("#auth").assertIsDisplayed()
        rule.onNodeWithText("#web").assertIsDisplayed()
    }

    @Test
    fun aRejectedCaptureShowsWhyUnderTheField() {
        rule.setContent {
            GsdTheme {
                CaptureBarContent(
                    state = CaptureState(text = "x".repeat(81), error = TaskValidationError.TitleLength()),
                    onTextChange = {},
                    onCycleOverride = {},
                    onSubmit = {},
                    onDetails = {},
                )
            }
        }
        rule.onNodeWithText("A title needs 1 to 80 characters.").assertIsDisplayed()
    }

    @Test
    fun overrideCyclePropagates() {
        assertEquals(Quadrant.DO_FIRST, CaptureState().cycled().override)
        assertEquals(Quadrant.SCHEDULE, CaptureState(override = Quadrant.DO_FIRST).cycled().override)
        assertEquals(null, CaptureState(override = Quadrant.ELIMINATE).cycled().override)
    }

    @Test
    fun effectiveQuadrantPrefersOverride() {
        assertEquals(Quadrant.ELIMINATE, CaptureState(text = "plain").effectiveQuadrant)
        assertEquals(Quadrant.DO_FIRST, CaptureState(text = "x !!").effectiveQuadrant)
        assertEquals(
            Quadrant.SCHEDULE,
            CaptureState(text = "x !!", override = Quadrant.SCHEDULE).effectiveQuadrant,
        )
    }
}
