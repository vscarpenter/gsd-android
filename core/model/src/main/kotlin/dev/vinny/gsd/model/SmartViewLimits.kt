package dev.vinny.gsd.model

/** Import/export bounds for custom smart views — the web's SCHEMA_LIMITS (S§7.1). */
object SmartViewLimits {
    const val MAX_SMART_VIEWS = 100
    const val ID_MAX = 128
    const val NAME_MAX = 80        // import bound; the editor keeps its 60
    const val ICON_MAX = 64
    const val SEARCH_MAX = 600
    const val FILTER_TAGS_MAX = 200
    const val TAG_MAX = 30
    const val PINNED_MAX = 5

    /** The first violated field name, or null when the view is within every bound. */
    fun violation(view: SmartView): String? = when {
        view.id.isEmpty() || view.id.length > ID_MAX -> "id"
        view.name.isEmpty() || view.name.length > NAME_MAX -> "name"
        view.icon.length > ICON_MAX -> "icon"
        view.criteria.searchQuery.length > SEARCH_MAX -> "searchQuery"
        view.criteria.tags.size > FILTER_TAGS_MAX || view.criteria.tags.any { it.isEmpty() || it.length > TAG_MAX } -> "tags"
        view.criteria.quadrants.size > 4 -> "quadrants"
        view.criteria.recurrence.size > 4 -> "recurrence"
        else -> null
    }
}
