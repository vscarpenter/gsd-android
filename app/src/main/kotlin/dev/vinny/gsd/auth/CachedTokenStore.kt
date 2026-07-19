package dev.vinny.gsd.auth

import dev.vinny.gsd.sync.TokenStore

/**
 * In-memory cache over the Keystore store so PocketBaseApi's synchronous
 * header lambda can read the token without suspending.
 */
class CachedTokenStore(private val inner: TokenStore) : TokenStore {
    @Volatile
    private var cached: String? = null

    @Volatile
    private var loaded = false

    suspend fun warm() {
        cached = inner.token()
        loaded = true
    }

    fun cachedToken(): String? = cached

    override suspend fun token(): String? {
        if (!loaded) warm()
        return cached
    }

    override suspend fun save(token: String) {
        inner.save(token)
        cached = token
        loaded = true
    }

    override suspend fun clear() {
        inner.clear()
        cached = null
        loaded = true
    }
}
