package dev.vinny.gsd.share

import dev.vinny.gsd.model.UrlSanitizer

data class ShareDraft(val title: String, val description: String)

/**
 * Shared-text → task draft (spec §12.2): URLs sanitized by the §8.2 rules move
 * to the description; a bare URL takes the sharing app's subject as its title,
 * as iOS takes the page title, or derives a readable one from host + path
 * words; the title is the one explicitly-clamped input (80 chars).
 */
object ShareCapture {
    private val urlPattern = Regex("""https?://\S+""")
    private val separators = Regex("""[-_/]+""")
    private val whitespace = Regex("""\s+""")

    fun buildDraft(sharedText: String, subject: String? = null): ShareDraft {
        val urls = mutableListOf<String>()
        val remainder = urlPattern.replace(sharedText) { match ->
            UrlSanitizer.sanitize(match.value)?.let { safe ->
                urls += safe
                ""
            } ?: match.value
        }.replace(whitespace, " ").trim()

        // Browsers share the page's own title as EXTRA_SUBJECT, a better title than the address (finding 9)
        val pageTitle = subject?.replace(whitespace, " ")?.trim()
            ?.takeIf { it.isNotEmpty() && !urlPattern.containsMatchIn(it) }
        val title = when {
            remainder.isNotEmpty() -> remainder
            urls.isNotEmpty() -> pageTitle ?: deriveTitle(urls.first())
            else -> "Review link below"
        }
        return ShareDraft(
            title = clampTitle(title),
            description = urls.joinToString("\n").take(600),
        )
    }

    /**
     * The address whose page title may replace [title] (spec §12.2), or null. Like iOS, only a title
     * still derived from the shared address qualifies, so a subject title or the user's own edit is
     * never overwritten. Only https: Android blocks cleartext traffic, as iOS's ATS does.
     */
    fun enrichableUrl(title: String, description: String): String? {
        val url = UrlSanitizer.sanitize(description.lineSequence().first()) ?: return null
        if (!url.startsWith("https://")) return null
        return url.takeIf { title == clampTitle(deriveTitle(it)) }
    }

    /** The first 80 UTF-16 units, the Android title limit, never ending in half a surrogate pair. */
    fun clampTitle(text: String): String {
        if (text.length <= TITLE_LIMIT) return text
        val cut = text.take(TITLE_LIMIT)
        return if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    private const val TITLE_LIMIT = 80

    /** "https://example.com/fancy-article_2026" → "example.com fancy article 2026" */
    private fun deriveTitle(url: String): String {
        val withoutScheme = url.substringAfter("://")
        val host = withoutScheme.substringBefore('/')
        val path = withoutScheme.substringAfter('/', "")
        val words = separators.split(path).filter { it.isNotBlank() }
        return (listOf(host) + words).joinToString(" ").trim().ifEmpty { "Review link below" }
    }
}
