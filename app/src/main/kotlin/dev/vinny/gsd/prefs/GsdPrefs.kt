package dev.vinny.gsd.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemePref { SYSTEM, LIGHT, DARK }

/** Lightweight preferences (spec §6.7 / §7.7): DataStore-backed, observed as Flows. */
class GsdPrefs(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val showCompleted = booleanPreferencesKey("showCompleted")
        val theme = stringPreferencesKey("theme")
        val hasOnboarded = booleanPreferencesKey("hasOnboarded")
        val fetchShareTitles = booleanPreferencesKey("fetchShareTitles")
        val lastOwnerId = stringPreferencesKey("lastOwnerId")
        val lastOwnerEmail = stringPreferencesKey("lastOwnerEmail")
    }

    val lastOwnerId: Flow<String?> = dataStore.data.map { it[Keys.lastOwnerId] }
    val lastOwnerEmail: Flow<String?> = dataStore.data.map { it[Keys.lastOwnerEmail] }

    suspend fun setLastOwner(id: String?, email: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.lastOwnerId) else prefs[Keys.lastOwnerId] = id
            if (email == null) prefs.remove(Keys.lastOwnerEmail) else prefs[Keys.lastOwnerEmail] = email
        }
    }

    val showCompleted: Flow<Boolean> = dataStore.data.map { it[Keys.showCompleted] ?: true }
    val theme: Flow<ThemePref> = dataStore.data.map { prefs ->
        prefs[Keys.theme]?.let { raw -> ThemePref.entries.firstOrNull { it.name == raw } } ?: ThemePref.SYSTEM
    }
    val hasOnboarded: Flow<Boolean> = dataStore.data.map { it[Keys.hasOnboarded] ?: false }
    val fetchShareTitles: Flow<Boolean> = dataStore.data.map { it[Keys.fetchShareTitles] ?: false }

    suspend fun setShowCompleted(value: Boolean) {
        dataStore.edit { it[Keys.showCompleted] = value }
    }

    suspend fun setTheme(value: ThemePref) {
        dataStore.edit { it[Keys.theme] = value.name }
    }

    suspend fun setHasOnboarded(value: Boolean) {
        dataStore.edit { it[Keys.hasOnboarded] = value }
    }

    suspend fun setFetchShareTitles(value: Boolean) {
        dataStore.edit { it[Keys.fetchShareTitles] = value }
    }
}
