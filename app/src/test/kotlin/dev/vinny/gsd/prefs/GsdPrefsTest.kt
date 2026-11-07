package dev.vinny.gsd.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.vinny.gsd.model.FeedbackDraft
import dev.vinny.gsd.model.Sentiment
import dev.vinny.gsd.model.SweepReminder
import java.time.Instant
import java.time.LocalTime
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

    /** Review follow-up S3: the email and provider of an unconfirmed sign-in outlive a restart. */
    @Test
    fun `a pending sign-in round-trips and clears`() = runBlocking {
        prefs.setPendingSignIn(PendingSignIn("user-b", "b@example.com", null))
        assertEquals(PendingSignIn("user-b", "b@example.com", null), prefs.pendingSignIn.first())

        prefs.clearPendingSignIn()

        assertEquals(null, prefs.pendingSignIn.first())
    }

    @Test
    fun `last swept and the sweep reminder round-trip`() = runBlocking {
        assertEquals(null, prefs.lastSweptAt.first())
        assertEquals(SweepReminder.OFF, prefs.sweepReminder.first())

        val swept = Instant.parse("2026-11-08T15:00:00Z")
        val reminder = SweepReminder(true, 1, LocalTime.of(7, 30))
        prefs.setLastSweptAt(swept)
        prefs.setSweepReminder(reminder)

        assertEquals(swept, prefs.lastSweptAt.first())
        assertEquals(reminder, prefs.sweepReminder.first())
    }

    /** Spec-weekly-sweep §6: a reset must clear both values, as B-16 did for the feedback draft. */
    @Test
    fun `clearSweepState clears last swept and the reminder`() = runBlocking {
        prefs.setLastSweptAt(Instant.parse("2026-11-08T15:00:00Z"))
        prefs.setSweepReminder(SweepReminder(true, 3, LocalTime.of(18, 0)))

        prefs.clearSweepState()

        assertEquals(null, prefs.lastSweptAt.first())
        assertEquals(SweepReminder.OFF, prefs.sweepReminder.first())
    }
}
