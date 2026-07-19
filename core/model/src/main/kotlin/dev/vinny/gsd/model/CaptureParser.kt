package dev.vinny.gsd.model

import java.net.URI

data class ParsedCapture(
    val title: String,
    val urgent: Boolean,
    val important: Boolean,
    val tags: List<String>,
    val urls: List<String>,
)

/**
 * Security-relevant URL gate (spec §8.2 — replicate exactly): http/https only,
 * no embedded credentials, valid hostname, < 2048 chars, trailing sentence
 * punctuation stripped. Null means unsafe: the URL stays in the title untouched.
 */
object UrlSanitizer {
    private val trailingPunctuation = charArrayOf(',', ';', ':', '.', '!', '?', ')')

    fun sanitize(candidate: String): String? {
        val trimmed = candidate.trimEnd(*trailingPunctuation)
        if (trimmed.length >= 2048) return null
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme != "http" && uri.scheme != "https") return null
        if (uri.userInfo != null) return null
        if (uri.host.isNullOrEmpty()) return null
        return trimmed
    }
}

/**
 * The capture shorthand grammar (spec §8.2): `!!` urgent+important, `!` urgent,
 * `*` important — all at word boundaries; `#tag` lowercased/deduped/max 20;
 * valid URLs move to the description. Appendix C vectors are the contract.
 */
object CaptureParser {
    private val urlPattern = Regex("""https?://\S+""")
    private val doubleBang = Regex("""(?<=^|\s)!!(?=\s|$|#)""")
    private val singleBang = Regex("""(?<=^|\s)!(?=\s|$|#)""")
    private val star = Regex("""(?<=^|\s)\*(?=\s|$|#)""")
    private val tagPattern = Regex("""#([^\s#]{1,30})""")
    private val whitespace = Regex("""\s+""")

    fun parse(input: String): ParsedCapture {
        val urls = mutableListOf<String>()
        var working = urlPattern.replace(input) { match ->
            val safe = UrlSanitizer.sanitize(match.value)
            if (safe != null) {
                urls += safe
                ""
            } else {
                match.value
            }
        }

        val both = doubleBang.containsMatchIn(working)
        working = doubleBang.replace(working, " ")
        val urgent = both || singleBang.containsMatchIn(working)
        working = singleBang.replace(working, " ")
        val important = both || star.containsMatchIn(working)
        working = star.replace(working, " ")

        val tags = tagPattern.findAll(working)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(20)
            .toList()
        working = tagPattern.replace(working, " ")

        val title = working.replace(whitespace, " ").trim()
        return ParsedCapture(
            title = if (title.isEmpty() && urls.isNotEmpty()) "Review link below" else title,
            urgent = urgent,
            important = important,
            tags = tags,
            urls = urls,
        )
    }
}
