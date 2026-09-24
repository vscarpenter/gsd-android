package dev.vinny.gsd.share

import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.model.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Upgrades a shared task's address-derived title to the page's own title, in the background (spec
 * §12.2, review finding 41), as iOS's ShareTitleEnricher does. It acts only while the setting is on
 * and the title is still the derived one: when it starts, and again inside the write, so a title
 * the user edited meanwhile is never overwritten. The task appears at once either way.
 */
internal fun enrichSharedTitle(container: AppContainer, task: Task, fetcher: PageTitleFetcher = PageTitleFetcher()) {
    val url = ShareCapture.enrichableUrl(task.title, task.description) ?: return
    val derived = task.title
    container.appScope.launch {
        if (!container.prefs.fetchShareTitles.first()) return@launch
        val fetched = withContext(Dispatchers.IO) { fetcher.title(url) } ?: return@launch
        if (container.store.get(task.id)?.title != derived) return@launch
        val title = ShareCapture.clampTitle(fetched)
        container.store.change(task.id) { current ->
            if (current.title == derived) current.copy(title = title) else current
        }
    }
}
