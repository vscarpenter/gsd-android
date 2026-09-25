package dev.vinny.gsd.share

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Review finding 41: the "Fetch titles for shared links" setting did nothing. */
class PageTitleFetcherTest {
    private lateinit var server: MockWebServer
    private val fetcher = PageTitleFetcher()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun page(html: String, contentType: String = "text/html; charset=utf-8") =
        server.enqueue(MockResponse().setHeader("Content-Type", contentType).setBody(html))

    @Test
    fun `reads the title of a page`() {
        page("<html><head><title>Android 17 review</title></head></html>")
        assertEquals("Android 17 review", fetcher.title(server.url("/review").toString()))
    }

    @Test
    fun `a failed request gives no title`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<title>Not Found</title>"))
        assertNull(fetcher.title(server.url("/missing").toString()))
    }

    @Test
    fun `reads no further than the cap`() {
        page(" ".repeat(70 * 1024) + "<title>Too far down</title>")
        assertNull(fetcher.title(server.url("/long").toString()))
    }

    @Test
    fun `honors the page's declared charset`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html; charset=ISO-8859-1")
                .setBody(Buffer().writeString("<title>café</title>", Charsets.ISO_8859_1)),
        )
        assertEquals("café", fetcher.title(server.url("/latin").toString()))
    }

    @Test
    fun `an unreachable server gives no title`() {
        val url = server.url("/gone").toString()
        server.shutdown()
        assertNull(fetcher.title(url))
    }
}
