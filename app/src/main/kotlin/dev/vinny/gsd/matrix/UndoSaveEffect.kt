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

/** Offers Undo after Move to Saved, on the same timing as [UndoDeleteEffect] (spec §8.20). */
@Composable
fun UndoSaveEffect(actions: TaskActionsViewModel, snackbarHost: SnackbarHostState) {
    val lastSaved by actions.lastSaved.collectAsStateWithLifecycle()
    val savedLabel = lastSaved?.let { stringResource(R.string.snackbar_saved, it.title) }
    val undoLabel = stringResource(R.string.snackbar_undo)
    val view = LocalView.current
    val accessibilityOn = remember(view) {
        (view.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isEnabled == true
    }
    LaunchedEffect(lastSaved) {
        val shown = lastSaved ?: return@LaunchedEffect
        if (savedLabel == null) return@LaunchedEffect
        var undone = false
        try {
            val result = withTimeoutOrNull(if (accessibilityOn) 12_000L else 6_000L) {
                snackbarHost.showSnackbar(message = savedLabel, actionLabel = undoLabel)
            }
            undone = result == SnackbarResult.ActionPerformed
        } finally {
            if (undone) actions.undoSave() else actions.clearSavedUndo(shown)
        }
    }
}
