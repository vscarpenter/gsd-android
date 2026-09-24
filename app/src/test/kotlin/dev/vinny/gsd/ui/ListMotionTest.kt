package dev.vinny.gsd.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review finding 33: no list animated, so completing a task jumped it to its new place. Motion has
 * no semantics a UI test can read, so this reads the source: every lazy `items(` block in :app
 * must apply [reorderMotion] to its row.
 */
class ListMotionTest {
    private val sources = File("src/main/kotlin")

    /** The trailing lambda of each `items(` call, braces balanced. */
    private fun itemBlocks(text: String): List<String> {
        val found = mutableListOf<String>()
        var from = text.indexOf("items(")
        while (from >= 0) {
            var paren = 0
            var at = from + "items".length
            do {
                when (text[at]) {
                    '(' -> paren++
                    ')' -> paren--
                }
                at++
            } while (paren > 0 && at < text.length)
            val open = text.indexOf('{', at)
            var brace = 0
            var end = open
            do {
                when (text[end]) {
                    '{' -> brace++
                    '}' -> brace--
                }
                end++
            } while (brace > 0 && end < text.length)
            found += text.substring(open, end)
            from = text.indexOf("items(", end)
        }
        return found
    }

    @Test
    fun everyListRowMovesInsteadOfJumping() {
        assertTrue(sources.isDirectory, "run from the :app module directory")
        val still = sources.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            itemBlocks(file.readText()).filter { "reorderMotion()" !in it }.map { file.name }
        }.toList()
        assertEquals(emptyList(), still)
    }
}
