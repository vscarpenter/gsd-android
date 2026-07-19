package dev.vinny.gsd.sync

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

data class JwtPayload(val userId: String, val expiresAt: Instant)

/**
 * Local JWT payload decode for `id` and `exp` only — deliberately no signature
 * verification (spec §10.3); the server is the authority.
 */
object Jwt {
    fun decodePayload(token: String): JwtPayload? {
        val segments = token.split('.')
        if (segments.size != 3) return null
        return try {
            val payload = Base64.getUrlDecoder().decode(segments[1]).toString(Charsets.UTF_8)
            val obj = Json.parseToJsonElement(payload).jsonObject
            val id = obj["id"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: return null
            val exp = obj["exp"]?.jsonPrimitive?.long ?: return null
            JwtPayload(id, Instant.ofEpochSecond(exp))
        } catch (_: Exception) {
            null
        }
    }
}
