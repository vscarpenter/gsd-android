package dev.vinny.gsd.prefs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

/** Review, "Also worth a look": no DataStore had a corruption handler, so one bad file failed every read. */
class ResetOnCorruptionTest {
    @Test
    fun aCorruptedFileReadsAsEmpty() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A varint that never ends: not a preferences file
        val file = File(context.cacheDir, "corrupt-${System.nanoTime()}.preferences_pb")
            .apply { writeBytes(ByteArray(8) { 0xFF.toByte() }) }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(
                corruptionHandler = resetOnCorruption,
                scope = scope,
                produceFile = { file },
            )
            assertEquals(emptyPreferences(), store.data.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }
}
