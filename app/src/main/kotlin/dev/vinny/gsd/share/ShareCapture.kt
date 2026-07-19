package dev.vinny.gsd.share

import dev.vinny.gsd.model.UrlSanitizer

data class ShareDraft(val title: String, val description: String)

/**
 * Shared-text → task draft (spec §12.2): URLs sanitized by the §8.2 rules move
 * to the description; a bare URL derives a readable title from host + path
 * words; the title is the one explicitly-clamped input (80 chars).
 */
object ShareCapture {
    private val urlPattern = Regex("""https?://\S+""")
    private val separators = Regex("""[-_/]+""")

    fun buildDraft(sharedText: String): ShareDraft {
        val urls = mutableListOf<String>()
        val remainder = urlPattern.replace(sharedText) { match ->
            UrlSanitizer.sanitize(match.value)?.let { safe ->
                urls += safe
                ""
            } ?: match.value
        }.replace(Regex("""\s+"""), " ").trim()

        val title = when {
            remainder.isNotEmpty() -> remainder
            urls.isNotEmpty() -> deriveTitle(urls.first())
            else -> "Review link below"
        }
        return ShareDraft(
            title = title.take(80),
            description = urls.joinToString("\n").take(600),
        )
    }

    /** "https://example.com/fancy-article_2026" → "example.com fancy article 2026" */
    private fun deriveTitle(url: String): String {
        val withoutScheme = url.substringAfter("://")
        val host = withoutScheme.substringBefore('/')
        val path = withoutScheme.substringAfter('/', "")
        val words = separators.split(path).filter { it.isNotBlank() }
        return (listOf(host) + words).joinToString(" ").trim().ifEmpty { "Review link below" }
    }
}
