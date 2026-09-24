package dev.vinny.gsd.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Review finding 28: both launcher shortcuts showed blank circles, and the widget picker showed a
 * generic preview, the label "GSD", and the title as the description. These live in resources that
 * no UI test can open, so this reads them.
 */
class LauncherSurfacesTest {
    private fun read(path: String): String {
        val file = File(path)
        assertTrue(file.isFile, "run from the :app module directory: $path")
        return file.readText()
    }

    @Test
    fun everyShortcutHasAnIcon() {
        val shortcuts = read("src/main/res/xml/shortcuts.xml")
        val declared = Regex("<shortcut\\b").findAll(shortcuts).count()
        assertTrue(declared > 0)
        assertEquals(declared, Regex("android:icon=\"@drawable/").findAll(shortcuts).count())
    }

    @Test
    fun theWidgetHasAPreviewAndItsOwnDescription() {
        val info = read("src/main/res/xml/gsd_widget_info.xml")
        assertTrue("android:previewLayout=\"@layout/" in info)
        assertFalse("android:description=\"@string/widget_title\"" in info)
    }

    @Test
    fun thePickerNamesTheWidget() {
        val manifest = read("src/main/AndroidManifest.xml")
        val receiver = manifest.substringAfter("android:name=\".widget.GsdWidgetReceiver\"").substringBefore("</receiver>")
        assertTrue("android:label=\"@string/widget_title\"" in receiver)
    }
}
