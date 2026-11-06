package dev.vinny.gsd.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import dev.vinny.gsd.BuildConfig
import dev.vinny.gsd.model.WhatsNew
import dev.vinny.gsd.model.WhatsNewPlatform
import dev.vinny.gsd.model.WhatsNewRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** This build's Android release notes, from the copy of the web repo's whats-new.json in :core:model. */
internal object WhatsNewContent {
    val runningVersion: String = BuildConfig.VERSION_NAME

    /** Android releases at or below the running version, newest first. Read once, off the main thread. */
    val androidReleases: List<WhatsNewRelease> by lazy {
        WhatsNew.releasesFor(WhatsNew.bundled().releases, WhatsNewPlatform.ANDROID, runningVersion)
    }
}

/** Null until the notes have been read from the bundled resource. */
@Composable
internal fun rememberAndroidReleases(): State<List<WhatsNewRelease>?> =
    produceState<List<WhatsNewRelease>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { WhatsNewContent.androidReleases }
    }
