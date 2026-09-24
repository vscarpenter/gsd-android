package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The same cases as iOS's PageTitleParserTests, so both platforms read a page the same way. */
class PageTitleParserTest {
    @Test
    fun `reads the title element`() =
        assertEquals("Hello World", PageTitleParser.parse("<html><head><title>Hello World</title></head><body>x</body></html>"))

    @Test
    fun `prefers og title over the title element`() =
        assertEquals(
            "OG Headline",
            PageTitleParser.parse("<head><meta property=\"og:title\" content=\"OG Headline\">\n<title>Tab Title</title></head>"),
        )

    @Test
    fun `reads og title with content before property`() =
        assertEquals("Reversed Attrs", PageTitleParser.parse("<meta content=\"Reversed Attrs\" property=\"og:title\" />"))

    @Test
    fun `decodes named entities`() =
        assertEquals("Tom & Jerry \"quoted\"", PageTitleParser.parse("<title>Tom &amp; Jerry &quot;quoted&quot;</title>"))

    @Test
    fun `decodes numeric entities`() =
        assertEquals("café ❤", PageTitleParser.parse("<title>caf&#233; &#x2764;</title>"))

    @Test
    fun `collapses whitespace and trims`() =
        assertEquals("Spaced Out", PageTitleParser.parse("<title>\n  Spaced   Out  \n</title>"))

    @Test
    fun `tags are case-insensitive`() =
        assertEquals("Caps", PageTitleParser.parse("<TITLE>Caps</TITLE>"))

    @Test
    fun `null when there is no title`() =
        assertNull(PageTitleParser.parse("<html><body>no title here</body></html>"))

    @Test
    fun `null for an empty title`() =
        assertNull(PageTitleParser.parse("<title>   </title>"))

    @Test
    fun `an emoji outside the basic plane decodes whole`() =
        assertEquals("💩", PageTitleParser.parse("<title>&#x1F4A9;</title>"))
}
