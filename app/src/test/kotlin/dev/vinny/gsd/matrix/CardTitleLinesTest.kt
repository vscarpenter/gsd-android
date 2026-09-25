package dev.vinny.gsd.matrix

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Review finding 35: card titles stopped at two lines with an ellipsis at every font scale, so at
 * 2.0 a title read "Renew passport befo...". Spec 5.4 lifts the cap at accessibility scales.
 */
class CardTitleLinesTest {
    @Test
    fun ordinaryScalesKeepTwoLines() {
        listOf(0.85f, 1.0f, 1.15f, 1.3f).forEach { scale ->
            assertEquals("scale $scale", 2, cardTitleMaxLines(scale))
        }
    }

    @Test
    fun accessibilityScalesLiftTheCap() {
        listOf(1.5f, 1.8f, 2.0f).forEach { scale ->
            assertEquals("scale $scale", Int.MAX_VALUE, cardTitleMaxLines(scale))
        }
    }
}
