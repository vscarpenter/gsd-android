package dev.vinny.gsd.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.vinny.gsd.sync.TokenStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(name = "gsd_auth")

/**
 * The PocketBase JWT encrypted with an AndroidKeyStore AES/GCM key; only the
 * ciphertext touches disk (spec §3). Never logged, never plain SharedPreferences.
 */
class KeystoreTokenStore(private val context: Context) : TokenStore {
    private val tokenKey = stringPreferencesKey("auth_token")

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    override suspend fun token(): String? {
        val stored = context.tokenDataStore.data.first()[tokenKey] ?: return null
        return try {
            val (ivPart, cipherPart) = stored.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(ivPart, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(cipherPart, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null // unreadable ciphertext (key rotated/cleared) → treated as signed out
        }
    }

    override suspend fun save(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        context.tokenDataStore.edit { it[tokenKey] = payload }
    }

    override suspend fun clear() {
        context.tokenDataStore.edit { it.remove(tokenKey) }
    }

    private companion object {
        const val KEY_ALIAS = "gsd-token-key"
    }
}
