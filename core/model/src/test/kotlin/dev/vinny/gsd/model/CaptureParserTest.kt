package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptureParserTest {
    @Test
    fun `appendix c vectors`() {
        with(CaptureParser.parse("Ship the deck !! #work")) {
            assertEquals("Ship the deck", title)
            assertEquals(true, urgent)
            assertEquals(true, important)
            assertEquals(listOf("work"), tags)
        }
        with(CaptureParser.parse("Call mom !")) {
            assertEquals("Call mom", title)
            assertEquals(true, urgent)
            assertEquals(false, important)
        }
        with(CaptureParser.parse("Plan trip *")) {
            assertEquals("Plan trip", title)
            assertEquals(false, urgent)
            assertEquals(true, important)
        }
        with(CaptureParser.parse("Read newsletter")) {
            assertEquals(false, urgent)
            assertEquals(false, important) // lands in Q4
        }
        with(CaptureParser.parse("Check https://example.com/a then report !")) {
            assertEquals("Check then report", title)
            assertEquals(true, urgent)
            assertEquals(listOf("https://example.com/a"), urls)
        }
        with(CaptureParser.parse("Read https://example.com/doc.")) {
            assertEquals(listOf("https://example.com/doc"), urls) // trailing '.' stripped
        }
        with(CaptureParser.parse("https://example.com")) {
            assertEquals("Review link below", title)
            assertEquals(listOf("https://example.com"), urls)
        }
        with(CaptureParser.parse("Fix login !!#auth #AUTH")) {
            assertEquals(listOf("auth"), tags)
            assertEquals(true, urgent)
            assertEquals(true, important)
        }
        with(CaptureParser.parse("Danger http://user:pass@evil.com")) {
            assertEquals("Danger http://user:pass@evil.com", title) // rejected URL stays in title
            assertEquals(emptyList(), urls)
        }
    }

    @Test
    fun `bang inside a word is not a token`() =
        assertEquals("wow!ok", CaptureParser.parse("wow!ok").title)

    @Test
    fun `tags are capped at 20`() {
        val input = "t " + (1..25).joinToString(" ") { "#tag$it" }
        assertEquals(20, CaptureParser.parse(input).tags.size)
    }

    @Test
    fun `sanitizer rejects non-http schemes, credentials, over-long, hostless`() {
        assertNull(UrlSanitizer.sanitize("ftp://example.com"))
        assertNull(UrlSanitizer.sanitize("javascript:alert(1)"))
        assertNull(UrlSanitizer.sanitize("https://user:pass@x.com"))
        assertNull(UrlSanitizer.sanitize("https://" + "a".repeat(2050)))
        assertNull(UrlSanitizer.sanitize("https://"))
        assertEquals("https://x.dev/p", UrlSanitizer.sanitize("https://x.dev/p,"))
    }
}
