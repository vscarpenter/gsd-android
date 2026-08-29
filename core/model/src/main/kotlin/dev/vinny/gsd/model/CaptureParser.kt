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
 * punctuation stripped. Scheme comparison is case-insensitive (parity spec
 * P§5 — "See HTTPS://EXAMPLE.COM" must sanitize). Null means unsafe: the URL
 * stays in the title untouched.
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
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        if (uri.host.isNullOrEmpty()) return null
        return trimmed
    }
}

/**
 * The capture shorthand grammar (parity spec P§5, which supersedes spec §8.2 +
 * Appendix C and is pinned by the shared 40-case cross-platform corpus):
 * - `!!` urgent+important, `!` urgent, `*` important — all at word boundaries;
 *   repeated tokens (`!! !!`) all strip in one pass.
 * - `#tag` — a tag starts at the beginning of the string or after whitespace
 *   (a hash glued to a word, e.g. `price#tag`, is not a tag) with a Unicode
 *   word character (letter/mark/number/underscore); `-` is allowed only as a
 *   continuation, so a leading hyphen (`#-dash`) does not parse as a tag.
 *   Tags lowercase and dedupe case-insensitively, cap at 20 by truncation
 *   (never rejected), and an over-length tag (>30) drops from the list but
 *   still strips from the title.
 * - URLs are word-boundary anchored, not just whitespace-anchored: a URL glued
 *   to a word (`foohttps://…`) stays literal, but one following punctuation
 *   (`(`, `"`, `:`) still extracts. Wrapping delimiters (`<>"'`{}|\^`) are
 *   excluded from the match itself so they stay put in the title; trailing
 *   sentence punctuation is peeled off by `UrlSanitizer`. Duplicate URLs
 *   collapse to the first occurrence; valid URLs move to the description,
 *   stored exactly as typed (never WHATWG-normalized).
 */
object CaptureParser {
    private val urlPattern = Regex(
        """\bhttps?://[^\s<>"'`{}|\\^]+""",
        RegexOption.IGNORE_CASE,
    )
    private val doubleBang = Regex("""(?<=^|\s)!!(?=\s|$|#)""")
    private val singleBang = Regex("""(?<=^|\s)!(?=\s|$|#)""")
    private val star = Regex("""(?<=^|\s)\*(?=\s|$|#)""")

    // Group 1 is the leading boundary (start-of-string or one whitespace char);
    // it is consumed so stripping a tag removes its lead-in space too — that's
    // what keeps "buy #milk, #eggs" -> "buy, now" rather than "buy , now".
    // Group 2 is unbounded on purpose: an over-length tag must still strip in
    // full (the length filter below only decides whether it's *kept*).
    private val tagPattern = Regex("""(^|\s)#([\p{L}\p{M}\p{N}_][\p{L}\p{M}\p{N}_-]*)""")
    private val whitespace = Regex("""\s+""")

    fun parse(input: String): ParsedCapture {
        val urls = LinkedHashSet<String>() // duplicates collapse to the first occurrence
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
            .map { it.groupValues[2] }
            .filter { it.length in 1..30 } // over-length drops from the list…
            .map { it.lowercase() } // …dedupe is case-insensitive
            .distinct()
            .take(20) // cap by truncation, never reject
            .toList()
        working = tagPattern.replace(working, "") // …but every match strips in full

        val title = working.replace(whitespace, " ").trim()
        return ParsedCapture(
            title = if (title.isEmpty() && urls.isNotEmpty()) "Review link below" else title,
            urgent = urgent,
            important = important,
            tags = tags,
            urls = urls.toList(),
        )
    }
}
