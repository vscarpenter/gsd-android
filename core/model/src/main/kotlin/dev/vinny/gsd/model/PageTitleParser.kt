package dev.vinny.gsd.model

/**
 * Pulls a page title out of raw HTML for shared-link enrichment (spec §12.2), as iOS's
 * `PageTitleParser` does: the Open Graph `og:title` first, then the `<title>` element, with common
 * entities decoded and whitespace collapsed. A light regex reader, not an HTML parser.
 */
object PageTitleParser {
    private val ogTitle = listOf(
        Regex("""<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE),
        Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+property=["']og:title["']""", RegexOption.IGNORE_CASE),
    )
    private val titleElement = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("""\s+""")
    private val numericEntity = Regex("""&#(x?)([0-9A-Fa-f]+);""")
    private val namedEntities = mapOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"",
        "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ",
    )
    private val surrogates = 0xD800..0xDFFF

    fun parse(html: String): String? {
        for (pattern in ogTitle) {
            pattern.find(html)?.let { clean(it.groupValues[1]) }?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return titleElement.find(html)?.let { clean(it.groupValues[1]) }?.takeIf { it.isNotEmpty() }
    }

    private fun clean(raw: String): String = decodeEntities(raw).replace(whitespace, " ").trim()

    private fun decodeEntities(text: String): String {
        val named = namedEntities.entries.fold(text) { acc, (entity, replacement) -> acc.replace(entity, replacement) }
        return numericEntity.replace(named) { match ->
            val (hex, digits) = match.destructured
            val code = digits.toIntOrNull(if (hex.isEmpty()) 10 else 16)
            // A lone surrogate is not a character, so it stays as written, as on iOS
            if (code != null && Character.isValidCodePoint(code) && code !in surrogates) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }
    }
}
