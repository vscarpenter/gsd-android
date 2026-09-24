package dev.vinny.gsd.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * A result or an error that appears after an action. TalkBack reads it when it appears or changes,
 * without moving focus (review, Accessibility: the app had no live regions).
 */
fun Modifier.announced(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
