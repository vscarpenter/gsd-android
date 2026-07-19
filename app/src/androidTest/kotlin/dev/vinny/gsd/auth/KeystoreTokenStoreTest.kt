package dev.vinny.gsd.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class KeystoreTokenStoreTest {
    @Test
    fun roundTripAndClear(): Unit = runBlocking {
        val store = KeystoreTokenStore(ApplicationProvider.getApplicationContext())
        store.save("header.payload.signature")
        assertEquals("header.payload.signature", store.token())
        store.clear()
        assertNull(store.token())
    }
}
