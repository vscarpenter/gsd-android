package dev.vinny.gsd.sync

import java.net.URLEncoder
import java.time.Clock
import java.time.Duration

/** Token persistence port — the Android impl is Keystore-encrypted (spec §3). */
interface TokenStore {
    suspend fun token(): String?
    suspend fun save(token: String)
    suspend fun clear()
}

/**
 * OAuth2 + PKCE via the web bounce (spec §10): PocketBase issues per-attempt
 * PKCE material; we hold it in memory only, validate `state` on the callback,
 * and never sign the user out on a transient refresh failure.
 */
class AuthManager(
    private val api: PocketBaseApi,
    private val tokens: TokenStore,
    private val clock: Clock,
    private val redirectUrl: String = "https://api.vinny.io/ios-oauth-redirect/",
) {
    class UnknownProvider(name: String) : Exception("Provider $name not configured")
    class StateMismatch : Exception("OAuth state mismatch")
    class MissingCode : Exception("OAuth callback carried no code")
    class InvalidToken : Exception("Token has no user id claim")

    data class PendingAuth(val provider: String, val state: String, val codeVerifier: String, val url: String)

    private var pending: PendingAuth? = null

    /** The provider of the most recent successful token exchange (spec P§7 two-tier Apple note). */
    var lastProvider: String? = null
        private set

    suspend fun beginOAuth(providerName: String): PendingAuth {
        val provider = api.authMethods().firstOrNull { it.name == providerName }
            ?: throw UnknownProvider(providerName)
        val url = provider.authURL + URLEncoder.encode(redirectUrl, "UTF-8")
        return PendingAuth(provider.name, provider.state, provider.codeVerifier, url)
            .also { pending = it }
    }

    suspend fun completeOAuth(callbackUri: String): AuthResult {
        val attempt = pending ?: throw StateMismatch()
        val params = queryParams(callbackUri)
        if (params["state"] != attempt.state) throw StateMismatch()
        val code = params["code"] ?: throw MissingCode()
        val result = api.authWithOAuth2(attempt.provider, code, attempt.codeVerifier, redirectUrl)
        if (Jwt.decodePayload(result.token) == null) throw InvalidToken()
        tokens.save(result.token)
        lastProvider = attempt.provider
        pending = null
        return result
    }

    suspend fun currentUserId(): String? =
        tokens.token()?.let { Jwt.decodePayload(it)?.userId }

    suspend fun tokenExpiresAt(): java.time.Instant? =
        tokens.token()?.let { Jwt.decodePayload(it)?.expiresAt }

    /**
     * Refresh inside the 7-day skew (spec §10.3). Transient failures fall back
     * to the still-valid stored token; only a definitive 401/403 clears it.
     */
    suspend fun ensureFreshToken() {
        val current = tokens.token() ?: return
        val payload = Jwt.decodePayload(current) ?: return
        val remaining = Duration.between(clock.instant(), payload.expiresAt)
        if (remaining > Duration.ofDays(7)) return
        try {
            tokens.save(api.authRefresh().token)
        } catch (definitive: PocketBaseApi.Unauthorized) {
            tokens.clear()
        } catch (_: Exception) {
            // transient (network/5xx): offline-first never signs the user out (spec §10.3)
        }
    }

    suspend fun signOut() = tokens.clear()

    companion object {
        fun queryParams(uri: String): Map<String, String> {
            val query = uri.substringAfter('?', "")
            if (query.isEmpty()) return emptyMap()
            return query.split('&').mapNotNull { pair ->
                val separator = pair.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = pair.substring(0, separator)
                val value = java.net.URLDecoder.decode(pair.substring(separator + 1), "UTF-8")
                key to value
            }.toMap()
        }
    }
}
