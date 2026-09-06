package dev.vinny.gsd.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.vinny.gsd.model.FeedbackDraft
import dev.vinny.gsd.model.Sentiment
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

/** Erase completeness (parity spec P§6.2): a saved feedback draft must not survive an erase. */
class GsdPrefsTest {
    private lateinit var storeFile: File
    private lateinit var prefs: GsdPrefs

    @Before
    fun setUp() {
        storeFile = File.createTempFile("gsd-prefs", ".preferences_pb").also { it.deleteOnExit() }
        prefs = GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { storeFile }))
    }

    @After
    fun tearDown() {
        storeFile.delete()
    }

    @Test
    fun `clearFeedbackDraft removes a saved draft entirely`() = runBlocking {
        prefs.setFeedbackDraft(
            FeedbackDraft(sentiment = Sentiment.UP, message = "still drafting", submissionId = "sub-erase"),
        )
        assertEquals("sub-erase", prefs.feedbackDraft.first()?.submissionId)

        prefs.clearFeedbackDraft()

        assertEquals(null, prefs.feedbackDraft.first())
    }
}
