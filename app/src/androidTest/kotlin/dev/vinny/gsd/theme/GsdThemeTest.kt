package dev.vinny.gsd.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import dev.vinny.gsd.model.Quadrant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class GsdThemeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lightSchemeMapsGsdTokens() {
        var primary = Color.Unspecified
        var background = Color.Unspecified
        var paper = Color.Unspecified
        var q1 = Color.Unspecified
        rule.setContent {
            GsdTheme(darkTheme = false) {
                primary = MaterialTheme.colorScheme.primary
                background = MaterialTheme.colorScheme.background
                paper = LocalGsdColors.current.paper
                q1 = LocalGsdColors.current.accent(Quadrant.DO_FIRST)
            }
        }
        assertEquals(Color(0xFF2C6680), primary)    // tint = Tide (spec §5.3)
        assertEquals(Color(0xFFF4F1E9), background) // paper (spec §5.2)
        assertEquals(background, paper)
        assertEquals(Color(0xFFB23A2E), q1)         // Rust (spec §5.1)
    }

    @Test
    fun darkSchemeMapsGsdTokens() {
        var primary = Color.Unspecified
        var ink = Color.Unspecified
        var q1Wash = Color.Unspecified
        rule.setContent {
            GsdTheme(darkTheme = true) {
                primary = MaterialTheme.colorScheme.primary
                ink = LocalGsdColors.current.ink
                q1Wash = LocalGsdColors.current.wash(Quadrant.DO_FIRST)
            }
        }
        assertEquals(Color(0xFF6FAACB), primary)  // Tide dark
        assertEquals(Color(0xFFF1ECE2), ink)      // ink dark
        assertEquals(Color(0xFF3A211D), q1Wash)   // Rust wash dark (spec §5.1)
    }
}
