package dev.vinny.gsd.nav

import dev.vinny.gsd.model.Quadrant

/**
 * The §6.4 deep-link routes — one router shared by widgets, shortcuts, and
 * notifications so `gsd://` links behave identically everywhere. The
 * `oauth-callback` host belongs to the auth flow and is ignored here.
 * The §6.6 keys go through the same router, so a key pressed on any tab
 * behaves like its link.
 */
sealed interface GsdRoute {
    data object Capture : GsdRoute

    /** Ctrl+K and Ctrl+F: Matrix with the palette open. Keyboard only; no `gsd://` link maps here. */
    data object Palette : GsdRoute
    data object Focus : GsdRoute
    data class QuadrantLink(val quadrant: Quadrant) : GsdRoute
    data class TaskLink(val id: String) : GsdRoute
    data class SmartViewLink(val id: String) : GsdRoute
    data object Dashboard : GsdRoute
    data object Settings : GsdRoute
    data object Archive : GsdRoute
    data object Trash : GsdRoute

    companion object {
        fun parse(uri: String): GsdRoute? {
            val withoutQuery = uri.substringBefore('?')
            if (!withoutQuery.startsWith("gsd://")) return null
            val segments = withoutQuery.removePrefix("gsd://").trimEnd('/').split('/')
            return when (segments.firstOrNull()) {
                "capture" -> Capture
                "focus" -> Focus
                "quadrant" -> segments.getOrNull(1)?.let(Quadrant::fromId)?.let(::QuadrantLink)
                "task" -> segments.getOrNull(1)?.takeIf { it.length >= 4 }?.let(::TaskLink)
                "smart-view" -> segments.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let(::SmartViewLink)
                "dashboard" -> Dashboard
                "settings" -> Settings
                "archive" -> Archive
                "trash" -> Trash
                else -> null
            }
        }
    }
}
