package dev.vinny.gsd.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    /** Review finding 9: Chrome shares the page title as EXTRA_SUBJECT and the address as EXTRA_TEXT. */
    @Test
    fun `a shared subject titles a bare url`() {
        val url = "https://www.theverge.com/2026/9/18/android-17-review"
        val draft = ShareCapture.buildDraft(url, subject = "Android 17 review: the best one yet")
        assertEquals("Android 17 review: the best one yet", draft.title)
        assertEquals(url, draft.description)
    }

    @Test
    fun `text beside the url wins over the subject`() =
        assertEquals("Read this later", ShareCapture.buildDraft("Read this later https://example.com/post", subject = "Some page").title)

    @Test
    fun `a subject that is only an address is ignored`() {
        val url = "https://example.com/fancy-article"
        assertEquals("example.com fancy article", ShareCapture.buildDraft(url, subject = url).title)
    }

    @Test
    fun `a blank subject falls back to the derived title`() =
        assertEquals("example.com fancy article", ShareCapture.buildDraft("https://example.com/fancy-article", subject = "  ").title)

    /** Review finding 41 and spec 12.2: only a title still derived from the shared https address is enriched. */
    @Test
    fun `a url-derived title is enrichable`() {
        val draft = ShareCapture.buildDraft("https://example.com/fancy-article")
        assertEquals("https://example.com/fancy-article", ShareCapture.enrichableUrl(draft.title, draft.description))
    }

    @Test
    fun `an edited title is not enrichable`() {
        val draft = ShareCapture.buildDraft("https://example.com/fancy-article")
        assertNull(ShareCapture.enrichableUrl("My own title", draft.description))
    }

    @Test
    fun `a title from the subject is not enrichable`() {
        val draft = ShareCapture.buildDraft("https://example.com/fancy-article", subject = "The real title")
        assertNull(ShareCapture.enrichableUrl(draft.title, draft.description))
    }

    @Test
    fun `a cleartext address is not enrichable`() {
        val draft = ShareCapture.buildDraft("http://example.com/fancy-article")
        assertNull(ShareCapture.enrichableUrl(draft.title, draft.description))
    }

    @Test
    fun `a clamped title never ends in half an emoji`() =
        assertEquals("x".repeat(79), ShareCapture.clampTitle("x".repeat(79) + "😀"))
}
