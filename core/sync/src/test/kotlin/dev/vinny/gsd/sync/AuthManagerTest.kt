package dev.vinny.gsd.sync

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTokenStore : TokenStore {
    var stored: String? = null
    override suspend fun token(): String? = stored
    override suspend fun save(token: String) { stored = token }
    override suspend fun clear() { stored = null }
}

class AuthManagerTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var server: MockWebServer
    private lateinit var tokens: FakeTokenStore
    private lateinit var auth: AuthManager

    private fun jwt(id: String?, expiresAt: Instant): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256"}""".toByteArray())
        val idField = id?.let { "\"id\":\"$it\"," } ?: ""
        val body = encoder.encodeToString("""{$idField"exp":${expiresAt.epochSecond}}""".toByteArray())
        return "$header.$body.sig"
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        tokens = FakeTokenStore()
        auth = AuthManager(
            api = PocketBaseApi(server.url("/").toString().trimEnd('/'), { tokens.stored }),
            tokens = tokens,
            clock = clock,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueueAuthMethods(state: String = "state-1") {
        server.enqueue(MockResponse().setBody(
            """{"oauth2":{"providers":[{"name":"google","displayName":"Google","state":"$state",
               "authURL":"https://accounts.google.com/o/oauth2/auth?client_id=x&redirect_uri=",
               "codeVerifier":"verifier-1","codeChallenge":"chal","codeChallengeMethod":"S256"}]}}""",
        ))
    }

    @Test
    fun `begin builds the provider url with the percent-encoded bounce redirect`() = runBlocking {
        enqueueAuthMethods()
        val pending = auth.beginOAuth("google")
        assertTrue(pending.url.endsWith("redirect_uri=https%3A%2F%2Fapi.vinny.io%2Fios-oauth-redirect%2F"))
        assertEquals("state-1", pending.state)
        assertEquals("verifier-1", pending.codeVerifier)
    }

    @Test
    fun `complete validates state and persists the token`() = runBlocking {
        enqueueAuthMethods("expected-state")
        auth.beginOAuth("google")
        val token = jwt("user-1", now.plusSeconds(14L * 86_400))
        server.enqueue(MockResponse().setBody("""{"token":"$token","record":{"id":"user-1","email":"v@x.dev"}}"""))
        val result = auth.completeOAuth("gsd://oauth-callback?code=CODE&state=expected-state")
        assertEquals("user-1", result.record.id)
        assertEquals(token, tokens.stored)
    }

    @Test
    fun `state mismatch aborts without exchanging`() = runBlocking {
        enqueueAuthMethods("expected-state")
        auth.beginOAuth("google")
        assertFailsWith<AuthManager.StateMismatch> {
            auth.completeOAuth("gsd://oauth-callback?code=CODE&state=tampered")
        }
        assertNull(tokens.stored)
        assertEquals(1, server.requestCount) // only auth-methods was called
    }

    @Test
    fun `token without an id claim fails fast and is not persisted`(): Unit = runBlocking {
        enqueueAuthMethods("s")
        auth.beginOAuth("google")
        val bad = jwt(id = null, expiresAt = now.plusSeconds(1000))
        server.enqueue(MockResponse().setBody("""{"token":"$bad","record":{"id":"user-1"}}"""))
        assertFailsWith<AuthManager.InvalidToken> {
            auth.completeOAuth("gsd://oauth-callback?code=C&state=s")
        }
        assertNull(tokens.stored)
    }

    @Test
    fun `ensureFresh refreshes inside the seven day skew`() = runBlocking {
        tokens.stored = jwt("user-1", now.plusSeconds(3L * 86_400)) // 3 days left < 7
        val newToken = jwt("user-1", now.plusSeconds(14L * 86_400))
        server.enqueue(MockResponse().setBody("""{"token":"$newToken","record":{"id":"user-1"}}"""))
        auth.ensureFreshToken()
        assertEquals(newToken, tokens.stored)
    }

    @Test
    fun `ensureFresh does nothing when the token is comfortably valid`() = runBlocking {
        val fresh = jwt("user-1", now.plusSeconds(10L * 86_400))
        tokens.stored = fresh
        auth.ensureFreshToken()
        assertEquals(fresh, tokens.stored)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `transient refresh failure keeps the stored token`() = runBlocking {
        val stale = jwt("user-1", now.plusSeconds(86_400))
        tokens.stored = stale
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        auth.ensureFreshToken() // must not throw, must not clear
        assertEquals(stale, tokens.stored)
    }

    @Test
    fun `definitive 401 clears the token`() = runBlocking {
        tokens.stored = jwt("user-1", now.plusSeconds(86_400))
        server.enqueue(MockResponse().setResponseCode(401))
        auth.ensureFreshToken()
        assertNull(tokens.stored)
    }

    @Test
    fun `current user id comes from the stored token`() = runBlocking {
        tokens.stored = jwt("user-42", now.plusSeconds(86_400))
        assertEquals("user-42", auth.currentUserId())
        tokens.stored = null
        assertNull(auth.currentUserId())
    }
}
