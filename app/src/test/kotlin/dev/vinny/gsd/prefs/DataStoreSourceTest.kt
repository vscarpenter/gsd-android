package dev.vinny.gsd.prefs

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every DataStore resets a file it cannot read, rather than failing each read (review, "Also worth a
 * look"). The delegate is a process singleton that a UI test cannot rebuild, so this reads the source.
 */
class DataStoreSourceTest {
    private val sources = File("src/main/kotlin")

    /** The argument list of each `by preferencesDataStore(...)` delegate. */
    private fun delegates(text: String): List<String> {
        val found = mutableListOf<String>()
        var from = text.indexOf("by preferencesDataStore(")
        while (from >= 0) {
            val start = text.indexOf('(', from)
            var end = start
            var depth = 0
            do {
                when (text[end]) {
                    '(' -> depth++
                    ')' -> depth--
                }
                end++
            } while (depth > 0 && end < text.length)
            found += text.substring(start, end)
            from = text.indexOf("by preferencesDataStore(", end)
        }
        return found
    }

    @Test
    fun everyDataStorePassesTheCorruptionHandler() {
        assertTrue(sources.isDirectory, "run from the :app module directory")
        val stores = sources.walkTopDown().filter { it.extension == "kt" }
            .flatMap { file -> delegates(file.readText()).map { file.name to it } }
            .toList()
        assertEquals(3, stores.size, "expected the prefs, auth, and widget stores: $stores")
        assertEquals(emptyList(), stores.filter { "corruptionHandler = resetOnCorruption" !in it.second }.map { it.first })
    }
}
