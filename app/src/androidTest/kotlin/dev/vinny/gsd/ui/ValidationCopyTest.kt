package dev.vinny.gsd.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.model.TaskValidationError
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Review, Copy: the editor, the capture bar, and the share sheet showed the model's exception
 * message, English written in Kotlin. People now read the app's own strings.
 */
class ValidationCopyTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun aTooLongTagIsNamedInTheMessage() {
        assertEquals(
            "The tag \"errands-for-the-whole-week-ahead\" is too long. A tag holds up to 30 characters.",
            resources.validationMessage(TaskValidationError.TagLength("errands-for-the-whole-week-ahead")),
        )
    }

    @Test
    fun theMessageComesFromResourcesNotTheModel() {
        val error = TaskValidationError.TitleLength()
        assertEquals("A title needs 1 to 80 characters.", resources.validationMessage(error))
        assertNotEquals(error.message, resources.validationMessage(error))
    }
}
