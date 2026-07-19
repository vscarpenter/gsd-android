package dev.vinny.gsd.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.vinny.gsd.R

/** The four §6.1 destinations. Icon mapping per spec §5.7. */
enum class Destination(
    val route: String,
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    MATRIX("matrix", R.string.nav_matrix, Icons.Outlined.GridView),
    BROWSE("browse", R.string.nav_browse, Icons.Outlined.FilterList),
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Outlined.BarChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
}
