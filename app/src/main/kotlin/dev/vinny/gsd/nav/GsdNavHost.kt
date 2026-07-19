package dev.vinny.gsd.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.matrix.MatrixScreen
import dev.vinny.gsd.prefs.ThemePref
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import kotlinx.coroutines.launch

@Composable
fun GsdNavHost(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    NavHost(navController = navController, startDestination = Destination.MATRIX.route, modifier = modifier) {
        composable(Destination.MATRIX.route) {
            MatrixScreen(container, expanded = expanded, onNavigate = { navController.navigate(it) })
        }
        composable(Destination.BROWSE.route) {
            dev.vinny.gsd.browse.BrowseScreen(
                container = container,
                onOpenArchive = { navController.navigate("archive") },
                onOpenView = { id -> navController.navigate("browse/view/$id") },
                onNewView = { navController.navigate("browse/edit") },
                onEditView = { id -> navController.navigate("browse/edit?id=$id") },
            )
        }
        composable("browse/view/{viewId}") { entry ->
            dev.vinny.gsd.browse.SmartViewListScreen(
                container = container,
                viewId = entry.arguments?.getString("viewId").orEmpty(),
            )
        }
        composable("browse/edit?id={id}") { entry ->
            dev.vinny.gsd.browse.SmartViewEditorScreen(
                container = container,
                viewId = entry.arguments?.getString("id"),
                onDone = { navController.popBackStack() },
            )
        }
        composable("archive") {
            dev.vinny.gsd.browse.ArchiveScreen(container = container)
        }
        composable(Destination.DASHBOARD.route) {
            PlaceholderScreen(R.string.dashboard_placeholder_title, R.string.dashboard_placeholder_body)
        }
        composable(Destination.SETTINGS.route) {
            SettingsScreen(container)
        }
    }
}

@Composable
private fun PlaceholderScreen(titleRes: Int, bodyRes: Int) {
    val gsd = LocalGsdColors.current
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(titleRes), style = GsdType.quadrantTitle, color = gsd.ink)
        Text(
            stringResource(bodyRes),
            style = GsdType.subhead,
            color = gsd.ink2,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

/** Minimal Appearance group; the full §8.17 settings surface lands in Phase 3. */
@Composable
private fun SettingsScreen(container: AppContainer) {
    val gsd = LocalGsdColors.current
    val theme by container.prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePref.SYSTEM)
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.settings_appearance).uppercase(),
            style = GsdType.caption,
            color = gsd.ink3,
        )
        Text(stringResource(R.string.settings_theme), style = GsdType.cardTitle, color = gsd.ink)
        val options = listOf(
            ThemePref.SYSTEM to R.string.theme_system,
            ThemePref.LIGHT to R.string.theme_light,
            ThemePref.DARK to R.string.theme_dark,
        )
        options.forEach { (pref, labelRes) ->
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(
                    selected = theme == pref,
                    onClick = { scope.launch { container.prefs.setTheme(pref) } },
                ),
            ) {
                RadioButton(selected = theme == pref, onClick = null)
                Text(stringResource(labelRes), style = GsdType.body, color = gsd.ink)
            }
        }
    }
}
