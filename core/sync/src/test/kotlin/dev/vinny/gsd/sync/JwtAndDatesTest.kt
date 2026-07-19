package dev.vinny.gsd.sync

import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtAndDatesTest {

    private fun token(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val body = encoder.encodeToString(payload.toByteArray())
        return "$header.$body.signature-not-checked"
    }

    @Test
    fun `decodes id and exp from the payload without verifying`() {
        val decoded = Jwt.decodePayload(token("""{"id":"user123","exp":1789600000,"type":"authRecord"}"""))!!
        assertEquals("user123", decoded.userId)
        assertEquals(Instant.ofEpochSecond(1_789_600_000), decoded.expiresAt)
    }

    @Test
    fun `missing id or malformed token yields null`() {
        assertNull(Jwt.decodePayload(token("""{"exp":123}""")))
        assertNull(Jwt.decodePayload("not-a-jwt"))
        assertNull(Jwt.decodePayload("a.b"))
    }

    @Test
    fun `dates parse both wire and space forms`() {
        val expected = Instant.parse("2026-06-10T12:00:00.123Z")
        assertEquals(expected, PbDates.parse("2026-06-10T12:00:00.123Z"))
        assertEquals(expected, PbDates.parse("2026-06-10 12:00:00.123Z"))
        assertEquals(Instant.parse("2026-06-10T12:00:00Z"), PbDates.parse("2026-06-10 12:00:00Z"))
        assertEquals(
            Instant.parse("2026-07-19T19:03:22.114Z"),
            PbDates.parse("2026-07-19T14:03:22.114-05:00"),
        )
        assertNull(PbDates.parse("garbage"))
        assertNull(PbDates.parse(""))
        assertNull(PbDates.parse(null))
    }

    @Test
    fun `wire format is fractional-ms T-Z and nil is empty string`() {
        assertEquals("2026-06-10T12:00:00.123Z", PbDates.formatWire(Instant.parse("2026-06-10T12:00:00.123Z")))
        assertEquals("", PbDates.formatWire(null))
    }

    @Test
    fun `cursor is space form with five second rewind`() {
        assertEquals(
            "2026-06-10 11:59:55.123Z",
            PbDates.cursorAfter(Instant.parse("2026-06-10T12:00:00.123Z")),
        )
    }
}
