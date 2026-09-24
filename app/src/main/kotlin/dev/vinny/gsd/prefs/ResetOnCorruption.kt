package dev.vinny.gsd.prefs

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

/**
 * What every GSD DataStore does with a file it cannot read: start over, empty. Without it, one
 * corrupted file failed every read of its store (review, "Also worth a look"). Starting over costs a
 * preference, the widget's snapshot until the next write, or a sign-in; failing every read cost the app.
 */
internal val resetOnCorruption: ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { emptyPreferences() }
