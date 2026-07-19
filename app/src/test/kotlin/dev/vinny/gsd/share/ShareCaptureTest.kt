package dev.vinny.gsd.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareCaptureTest {
    @Test
    fun `plain text becomes the title`() {
        val draft = ShareCapture.buildDraft("Pick up the dry cleaning")
        assertEquals("Pick up the dry cleaning", draft.title)
        assertEquals("", draft.description)
    }

    @Test
    fun `bare url derives a readable title and lands in the description`() {
        val draft = ShareCapture.buildDraft("https://example.com/fancy-article_2026")
        assertEquals("example.com fancy article 2026", draft.title)
        assertEquals("https://example.com/fancy-article_2026", draft.description)
    }

    @Test
    fun `mixed text and url splits into title and description`() {
        val draft = ShareCapture.buildDraft("Read this later https://example.com/post")
        assertEquals("Read this later", draft.title)
        assertEquals("https://example.com/post", draft.description)
    }

    @Test
    fun `unsafe url stays in the title untouched`() {
        val draft = ShareCapture.buildDraft("http://user:pass@evil.com")
        assertEquals("http://user:pass@evil.com", draft.title)
        assertEquals("", draft.description)
    }

    @Test
    fun `empty remainder with no url falls back to review copy`() {
        val draft = ShareCapture.buildDraft("   ")
        assertEquals("Review link below", draft.title)
    }

    @Test
    fun `clamps title to 80 and description to 600`() {
        val longText = "x".repeat(200)
        assertEquals(80, ShareCapture.buildDraft(longText).title.length)
        val manyUrls = (1..30).joinToString(" ") { "https://example.com/page-$it-padding-padding" }
        assertTrue(ShareCapture.buildDraft(manyUrls).description.length <= 600)
    }

    @Test
    fun `multiple urls join with newlines`() {
        val draft = ShareCapture.buildDraft("https://a.dev/one https://b.dev/two")
        assertEquals("https://a.dev/one\nhttps://b.dev/two", draft.description)
    }
}
