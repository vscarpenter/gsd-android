package dev.vinny.gsd.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.matrix.MatrixScreen
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

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
            dev.vinny.gsd.dashboard.DashboardScreen(
                container = container,
                onOpenOverdue = { navController.navigate("browse/view/overdue") },
            )
        }
        composable(Destination.SETTINGS.route) {
            dev.vinny.gsd.settings.SettingsScreen(
                container = container,
                onOpenHelp = { navController.navigate("help") },
            )
        }
        composable("help") {
            val gsd = LocalGsdColors.current
            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.help_title), style = GsdType.title, color = gsd.ink)
                Text(stringResource(R.string.help_body), style = GsdType.body, color = gsd.ink2)
            }
        }
    }
}

