package dev.vinny.gsd.matrix

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.R
import kotlinx.coroutines.withTimeoutOrNull

/** Offers Undo after a delete (spec §8.20). Every screen that can delete a task row hosts one. */
@Composable
fun UndoDeleteEffect(actions: TaskActionsViewModel, snackbarHost: SnackbarHostState) {
    val lastDeleted by actions.lastDeleted.collectAsStateWithLifecycle()
    val deletedLabel = lastDeleted?.let { stringResource(R.string.snackbar_deleted, it.title) }
    val undoLabel = stringResource(R.string.snackbar_undo)
    val view = LocalView.current
    val accessibilityOn = remember(view) {
        (view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isEnabled == true
    }
    LaunchedEffect(lastDeleted) {
        if (lastDeleted == null || deletedLabel == null) return@LaunchedEffect
        // ~6 s to undo, extended to ~12 s under TalkBack (spec §8.20)
        val result = withTimeoutOrNull(if (accessibilityOn) 12_000L else 6_000L) {
            snackbarHost.showSnackbar(message = deletedLabel, actionLabel = undoLabel)
        }
        if (result == SnackbarResult.ActionPerformed) {
            actions.undoDelete()
        } else {
            snackbarHost.currentSnackbarData?.dismiss()
            actions.clearUndo()
        }
    }
}
