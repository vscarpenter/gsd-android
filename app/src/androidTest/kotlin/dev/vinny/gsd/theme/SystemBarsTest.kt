package dev.vinny.gsd.theme

import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.MainActivity
import dev.vinny.gsd.prefs.ThemePref
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse

/**
 * Review finding 38: the status bar icons followed the system theme, so a dark in-app theme on a
 * light system drew dark icons on dark paper. Changes the theme preference and puts it back.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarsTest {
    private val app = ApplicationProvider.getApplicationContext<GsdApp>()
    private var before = ThemePref.SYSTEM

    @Before
    fun rememberTheme(): Unit = runBlocking { before = app.container.prefs.theme.first() }

    @After
    fun restoreTheme(): Unit = runBlocking { app.container.prefs.setTheme(before) }

    @Test
    fun barIconsFollowTheInAppTheme() {
        val systemDark = (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        assumeFalse("needs a light system theme to tell the two apart", systemDark)
        runBlocking { app.container.prefs.setTheme(ThemePref.DARK) }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var lightIcons = true
            val deadline = System.currentTimeMillis() + 5_000
            while (lightIcons && System.currentTimeMillis() < deadline) {
                scenario.onActivity {
                    lightIcons = WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars
                }
                if (lightIcons) Thread.sleep(50)
            }
            assertFalse(lightIcons, "dark paper needs light status bar icons")
        }
    }
}
