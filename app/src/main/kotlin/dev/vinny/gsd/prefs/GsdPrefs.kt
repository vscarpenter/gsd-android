package dev.vinny.gsd.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.vinny.gsd.model.FeedbackCategory
import dev.vinny.gsd.model.FeedbackDraft
import dev.vinny.gsd.model.Sentiment
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
        val lastAuthProvider = stringPreferencesKey("lastAuthProvider")
        val feedbackSubmissionId = stringPreferencesKey("feedbackSubmissionId")
        val feedbackSentiment = stringPreferencesKey("feedbackSentiment")
        val feedbackCategory = stringPreferencesKey("feedbackCategory")
        val feedbackMessage = stringPreferencesKey("feedbackMessage")
        val feedbackVotes = stringPreferencesKey("feedbackVotes")
    }

    val lastOwnerId: Flow<String?> = dataStore.data.map { it[Keys.lastOwnerId] }
    val lastOwnerEmail: Flow<String?> = dataStore.data.map { it[Keys.lastOwnerEmail] }
    val lastAuthProvider: Flow<String?> = dataStore.data.map { it[Keys.lastAuthProvider] }

    suspend fun setLastOwner(id: String?, email: String?) {
        dataStore.edit { prefs ->
            if (id == null) prefs.remove(Keys.lastOwnerId) else prefs[Keys.lastOwnerId] = id
            if (email == null) prefs.remove(Keys.lastOwnerEmail) else prefs[Keys.lastOwnerEmail] = email
        }
    }

    /** The provider of the most recent successful sign-in (spec P§7 two-tier Apple note). */
    suspend fun setLastAuthProvider(provider: String?) {
        dataStore.edit { prefs ->
            if (provider == null) prefs.remove(Keys.lastAuthProvider) else prefs[Keys.lastAuthProvider] = provider
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

    /** The in-progress feedback draft (parity spec P§6.2) — null once no submission id is persisted. */
    val feedbackDraft: Flow<FeedbackDraft?> = dataStore.data.map { prefs ->
        val submissionId = prefs[Keys.feedbackSubmissionId] ?: return@map null
        FeedbackDraft(
            sentiment = prefs[Keys.feedbackSentiment]?.let { wire -> Sentiment.entries.firstOrNull { it.wire == wire } },
            category = prefs[Keys.feedbackCategory]?.let { wire -> FeedbackCategory.entries.firstOrNull { it.wire == wire } },
            message = prefs[Keys.feedbackMessage].orEmpty(),
            votes = prefs[Keys.feedbackVotes]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            submissionId = submissionId,
        )
    }

    /** Persists the whole draft, including the durable submission id (survives process death). */
    suspend fun setFeedbackDraft(draft: FeedbackDraft) {
        dataStore.edit { prefs ->
            prefs[Keys.feedbackSubmissionId] = draft.submissionId
            val sentiment = draft.sentiment
            if (sentiment == null) prefs.remove(Keys.feedbackSentiment) else prefs[Keys.feedbackSentiment] = sentiment.wire
            val category = draft.category
            if (category == null) prefs.remove(Keys.feedbackCategory) else prefs[Keys.feedbackCategory] = category.wire
            prefs[Keys.feedbackMessage] = draft.message
            prefs[Keys.feedbackVotes] = draft.votes.joinToString(",")
        }
    }

    /** Clears every persisted draft field — called after a successful send. */
    suspend fun clearFeedbackDraft() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.feedbackSubmissionId)
            prefs.remove(Keys.feedbackSentiment)
            prefs.remove(Keys.feedbackCategory)
            prefs.remove(Keys.feedbackMessage)
            prefs.remove(Keys.feedbackVotes)
        }
    }
}
