package dev.vinny.gsd.share

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Review finding 9 and "Also worth a look": the share sheet ignored EXTRA_SUBJECT, so a shared page
 * got its address as a title, and it dropped EXTRA_TEXT that arrived as styled text. Nothing here
 * taps Add, so it writes nothing.
 */
@RunWith(AndroidJUnit4::class)
class ShareActivityTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun share(text: CharSequence, subject: String? = null): Intent =
        Intent(context, ShareActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    @Test
    fun aBrowsersPageTitleBecomesTheTaskTitle() {
        val intent = share("https://www.theverge.com/2026/9/18/android-17-review", subject = "Android 17 review")
        ActivityScenario.launch<ShareActivity>(intent).use {
            rule.onNodeWithTag("share_title").assertTextEquals("Android 17 review")
        }
    }

    @Test
    fun styledSharedTextStillArrives() {
        ActivityScenario.launch<ShareActivity>(share(SpannableString("Buy oat milk"))).use {
            rule.onNodeWithTag("share_title").assertTextEquals("Buy oat milk")
        }
    }
}
