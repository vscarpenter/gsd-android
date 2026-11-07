package dev.vinny.gsd.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.matrix.MatrixScreen
import dev.vinny.gsd.matrix.ScreenTopBar

@Composable
fun GsdNavHost(
    navController: NavHostController,
    container: AppContainer,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    // Every pushed screen gets an Up arrow (review finding 21)
    val up: () -> Unit = { navController.navigateUp() }
    NavHost(navController = navController, startDestination = Destination.MATRIX.route, modifier = modifier) {
        composable(Destination.MATRIX.route) {
            MatrixScreen(container, expanded = expanded, onNavigate = navController::navigateToRoute)
        }
        composable(Destination.BROWSE.route) {
            WithTopBar(stringResource(R.string.nav_browse)) { content ->
                dev.vinny.gsd.browse.BrowseScreen(
                    container = container,
                    onOpenArchive = { navController.navigate("archive") },
                    onOpenSaved = { navController.navigate("saved") },
                    onOpenTrash = { navController.navigate("trash") },
                    onOpenView = { id -> navController.navigate("browse/view/$id") },
                    onNewView = { navController.navigate("browse/edit") },
                    onEditView = { id -> navController.navigate("browse/edit?id=$id") },
                    modifier = content,
                )
            }
        }
        // These draw their own bar: it carries the view's name or the screen's actions
        composable("browse/view/{viewId}") { entry ->
            dev.vinny.gsd.browse.SmartViewListScreen(
                container = container,
                viewId = entry.arguments?.getString("viewId").orEmpty(),
                onUp = up,
            )
        }
        composable("sweep") {
            dev.vinny.gsd.sweep.SweepScreen(
                container = container,
                onUp = up,
                // Always the matrix, even when the sweep was opened from Settings (spec decision S4)
                onBackToMatrix = { navController.navigateToRoute("matrix") },
            )
        }
        composable("saved") {
            dev.vinny.gsd.browse.SavedScreen(container = container, onUp = up)
        }
        composable("archive") {
            dev.vinny.gsd.browse.ArchiveScreen(container = container, onUp = up)
        }
        composable("trash") {
            dev.vinny.gsd.browse.TrashScreen(container = container, onUp = up)
        }
        composable("browse/edit?id={id}") { entry ->
            WithTopBar(stringResource(R.string.view_editor_title), onUp = up) { content ->
                dev.vinny.gsd.browse.SmartViewEditorScreen(
                    container = container,
                    viewId = entry.arguments?.getString("id"),
                    onDone = { navController.popBackStack() },
                    modifier = content,
                )
            }
        }
        composable(Destination.DASHBOARD.route) {
            WithTopBar(stringResource(R.string.nav_dashboard)) { content ->
                dev.vinny.gsd.dashboard.DashboardScreen(
                    container = container,
                    onOpenOverdue = { navController.navigate("browse/view/overdue") },
                    modifier = content,
                )
            }
        }
        composable(Destination.SETTINGS.route) {
            WithTopBar(stringResource(R.string.nav_settings)) { content ->
                dev.vinny.gsd.settings.SettingsScreen(
                    container = container,
                    onOpenHelp = { navController.navigate("help") },
                    modifier = content,
                    onOpenSyncHistory = { navController.navigate("sync-history") },
                    onOpenFeedback = { navController.navigate("feedback") },
                    onOpenWhatsNew = { navController.navigate("whats-new") },
                    onOpenSweep = { navController.navigate("sweep") },
                )
            }
        }
        composable("sync-history") {
            WithTopBar(stringResource(R.string.history_title), onUp = up) { content ->
                dev.vinny.gsd.settings.SyncHistoryScreen(container = container, modifier = content)
            }
        }
        composable("feedback") {
            WithTopBar(stringResource(R.string.feedback_title), onUp = up) { content ->
                dev.vinny.gsd.feedback.FeedbackScreen(container = container, modifier = content)
            }
        }
        composable("whats-new") {
            WithTopBar(stringResource(R.string.whats_new_title), onUp = up) { content ->
                dev.vinny.gsd.whatsnew.WhatsNewScreen(container = container, modifier = content)
            }
        }
        composable("help") {
            WithTopBar(stringResource(R.string.help_title), onUp = up) { content ->
                dev.vinny.gsd.help.FieldGuideScreen(modifier = content)
            }
        }
    }
}

/** A screen under the shared top bar; [content] gets the modifier that fills the space below it. */
@Composable
private fun WithTopBar(title: String, onUp: (() -> Unit)? = null, content: @Composable (Modifier) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTopBar(title, onUp = onUp)
        content(Modifier.weight(1f).fillMaxWidth())
    }
}

