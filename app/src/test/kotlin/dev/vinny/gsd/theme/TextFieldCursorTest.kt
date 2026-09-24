package dev.vinny.gsd.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review finding 10: BasicTextField draws a black caret unless told otherwise, and black is invisible
 * on dark paper. The caret has no semantics a UI test can read, so this reads the source instead:
 * every BasicTextField in :app must pass a cursorBrush. It also checks every field for a decoration
 * box, where its hint names it for TalkBack (review, Accessibility).
 */
class TextFieldCursorTest {
    private val sources = File("src/main/kotlin")

    /** Each BasicTextField( call and the text up to its closing parenthesis. */
    private fun calls(text: String): List<String> {
        val found = mutableListOf<String>()
        var from = text.indexOf("BasicTextField(")
        while (from >= 0) {
            var depth = 0
            var end = from + "BasicTextField".length
            do {
                when (text[end]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                end++
            } while (depth > 0 && end < text.length)
            found += text.substring(from, end)
            from = text.indexOf("BasicTextField(", end)
        }
        return found
    }

    @Test
    fun everyTextFieldSetsItsCursor() {
        assertTrue(sources.isDirectory, "run from the :app module directory")
        val missing = sources.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            calls(file.readText()).filter { "cursorBrush" !in it }.map { file.name }
        }.toList()
        assertEquals(emptyList(), missing)
    }

    /** A hint beside a field was read as loose text and the field as an unnamed edit box. */
    @Test
    fun everyTextFieldCarriesItsHintInside() {
        val unnamed = sources.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            calls(file.readText()).filter { "decorationBox" !in it }.map { file.name }
        }.toList()
        assertEquals(emptyList(), unnamed)
    }
}
