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

    /** Review finding 23: an unmapped role keeps Material's baseline purple. */
    @Test
    fun theRolesStockComponentsReadAreGsdTokens() {
        var roles: androidx.compose.material3.ColorScheme? = null
        rule.setContent { GsdTheme(darkTheme = false) { roles = MaterialTheme.colorScheme } }
        val scheme = roles!!
        assertEquals(GsdColorsLight.tint, scheme.secondary, "secondary colors the selected navigation label")
        assertEquals(GsdColorsLight.tint, scheme.tertiary)
        assertEquals(GsdColorsLight.tideWash, scheme.primaryContainer, "the time picker's selected field")
        assertEquals(GsdColorsLight.tideWash, scheme.tertiaryContainer, "the time picker's AM or PM")
        assertEquals(GsdColorsLight.alertWash, scheme.errorContainer)
    }

    @Test
    fun darkRolesAreGsdTokensToo() {
        var secondary = Color.Unspecified
        var container = Color.Unspecified
        rule.setContent {
            GsdTheme(darkTheme = true) {
                secondary = MaterialTheme.colorScheme.secondary
                container = MaterialTheme.colorScheme.primaryContainer
            }
        }
        assertEquals(GsdColorsDark.tint, secondary)
        assertEquals(GsdColorsDark.tideWash, container)
    }

    /** Review finding 24: chips fell back to 8dp rectangles, menus and snackbars to 4dp, dialogs to 28dp. */
    @Test
    fun stockComponentsTakeGsdShapesAndType() {
        var shapes: androidx.compose.material3.Shapes? = null
        var typography: androidx.compose.material3.Typography? = null
        rule.setContent {
            GsdTheme {
                shapes = MaterialTheme.shapes
                typography = MaterialTheme.typography
            }
        }
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(percent = 50), shapes!!.small) // chips
        assertEquals(GsdShapes.small, shapes!!.extraSmall)  // menus, snackbars
        assertEquals(GsdShapes.sheet, shapes!!.extraLarge)  // dialogs, sheets
        assertEquals(GsdType.body, typography!!.bodyLarge)
        assertEquals(GsdType.cardTitle, typography!!.headlineSmall) // dialog titles
    }
}
