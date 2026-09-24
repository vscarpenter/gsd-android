package dev.vinny.gsd.share

import dev.vinny.gsd.model.PageTitleParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * Fetches a web page's title for shared-link enrichment (spec §12.2), as iOS's PageTitleFetcher
 * does: at most 64 KB, enough for the head, an 8-second timeout, and null on any failure, so the
 * caller keeps the title it derived offline. Blocking, so call it off the main thread.
 */
class PageTitleFetcher(private val timeoutMs: Int = 8_000, private val maxBytes: Int = 64 * 1024) {
    fun title(url: String): String? {
        val connection = try {
            URL(url).openConnection() as? HttpURLConnection
        } catch (_: IOException) {
            null
        } ?: return null
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Accept", "text/html")
            if (connection.responseCode !in 200..299) return null
            val head = connection.inputStream.use { it.readUpTo(maxBytes) }
            PageTitleParser.parse(String(head, charsetOf(connection.contentType)))
        } catch (_: IOException) {
            null // offline, refused, or timed out: the derived title stays
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** The charset the server declares, else UTF-8. */
    private fun charsetOf(contentType: String?): Charset =
        contentType?.substringAfter("charset=", "")?.substringBefore(';')?.trim()?.trim('"')
            ?.takeIf { it.isNotEmpty() }
            ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
            ?: Charsets.UTF_8
}
