package dev.vinny.gsd.theme

import java.io.File
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Review, Accessibility: `ink3` measured 4.17:1 on paper and 3.81:1 on sunken, and ochre 4.17:1 on
 * its wash. Text must reach WCAG AA, 4.5:1, on every surface it sits on (spec §13.3). This reads
 * the token values from GsdColors.kt, so it runs without the Android SDK.
 */
class ContrastTest {
    private val source = File("src/main/kotlin/dev/vinny/gsd/theme/GsdColors.kt").readText()

    /** The `token = Color(0xFFrrggbb)` pairs of one palette, by token name. */
    private fun palette(name: String): Map<String, String> {
        val body = source.substringAfter("val $name = GsdColors(").substringBefore("\n)")
        return Regex("""(\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)""").findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun luminance(hex: String): Double {
        fun channel(i: Int): Double {
            val c = hex.substring(i, i + 2).toInt(16) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
    }

    private fun contrast(a: String, b: String): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun failures(colors: Map<String, String>, inks: List<String>, grounds: List<String>) =
        inks.flatMap { ink -> grounds.map { ground -> ink to ground } }
            .filter { (ink, ground) -> contrast(colors.getValue(ink), colors.getValue(ground)) < 4.5 }
            .map { (ink, ground) -> "$ink on $ground %.2f".format(contrast(colors.getValue(ink), colors.getValue(ground))) }

    @Test
    fun textInksMeetAaOnEverySurface() {
        for (name in listOf("GsdColorsLight", "GsdColorsDark")) {
            val colors = palette(name)
            val failed = failures(
                colors,
                inks = listOf("ink", "ink2", "ink3", "tint", "alert", "success"),
                grounds = listOf("paper", "sunken", "surface", "surface2"),
            ) + failures(colors, listOf("alert"), listOf("alertWash"))
            assertTrue(failed.isEmpty(), "$name: $failed")
        }
    }

    @Test
    fun quadrantTextInksMeetAaOnTheirWashAndThePage() {
        val inkAndWash = listOf("rust" to "rustWash", "tide" to "tideWash", "ochreInk" to "ochreWash", "slateInk" to "slateWash")
        for (name in listOf("GsdColorsLight", "GsdColorsDark")) {
            val colors = palette(name)
            val failed = inkAndWash.flatMap { (ink, wash) ->
                failures(colors, listOf(ink), listOf(wash, "paper", "sunken", "surface"))
            }
            assertTrue(failed.isEmpty(), "$name: $failed")
        }
    }

    /** The Mark urgent swipe button writes its label in inkOnAccent on the target quadrant's ink. */
    @Test
    fun markUrgentButtonLabelMeetsAaOnItsBackground() {
        for (name in listOf("GsdColorsLight", "GsdColorsDark")) {
            val colors = palette(name)
            val failed = failures(colors, listOf("inkOnAccent"), listOf("rust", "ochreInk"))
            assertTrue(failed.isEmpty(), "$name: $failed")
        }
    }
}
