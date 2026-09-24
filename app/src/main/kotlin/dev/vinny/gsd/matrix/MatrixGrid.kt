package dev.vinny.gsd.matrix

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.model.DependencyGraph
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task

/**
 * Expanded-width matrix (spec §6.2): a true 2×2 grid — Q1 TL, Q2 TR, Q3 BL,
 * Q4 BR — with cross-quadrant drag-and-drop reclassification. Cards carry the same §8.1 menu as
 * the phone list (review finding 14).
 */
@Composable
fun MatrixGrid(
    sections: List<QuadrantSectionState>,
    onToggleComplete: (Task) -> Unit,
    onOpen: (String) -> Unit,
    onDropToQuadrant: (String, Quadrant) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (Task) -> Unit = {},
    rowActions: ((Task) -> TaskRowActions)? = null,
    menuEnabled: Boolean = true,
) {
    val byQuadrant = sections.associateBy { it.quadrant }
    val allVisible = sections.flatMap { it.tasks }
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        listOf(
            listOf(Quadrant.DO_FIRST, Quadrant.SCHEDULE),
            listOf(Quadrant.DELEGATE, Quadrant.ELIMINATE),
        ).forEach { rowQuadrants ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                rowQuadrants.forEach { quadrant ->
                    QuadrantCell(
                        section = byQuadrant.getValue(quadrant),
                        allVisible = allVisible,
                        onToggleComplete = onToggleComplete,
                        onOpen = onOpen,
                        onDropToQuadrant = onDropToQuadrant,
                        onDelete = onDelete,
                        rowActions = rowActions,
                        menuEnabled = menuEnabled,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuadrantCell(
    section: QuadrantSectionState,
    allVisible: List<Task>,
    onToggleComplete: (Task) -> Unit,
    onOpen: (String) -> Unit,
    onDropToQuadrant: (String, Quadrant) -> Unit,
    onDelete: (Task) -> Unit,
    rowActions: ((Task) -> TaskRowActions)?,
    menuEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val dropTarget = remember(section.quadrant) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val taskId = event.toAndroidDragEvent().clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.text?.toString()
                if (taskId != null) onDropToQuadrant(taskId, section.quadrant)
                return taskId != null
            }
        }
    }
    Column(
        modifier = modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
            },
            target = dropTarget,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuadrantHeader(section)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(section.tasks, key = { it.id }) { task ->
                val badges = TaskBadges(
                    blocked = DependencyGraph.uncompletedBlockers(allVisible, task).size,
                    blocking = DependencyGraph.blocking(allVisible, task).size,
                )
                SwipeTaskRow(
                    task = task,
                    onComplete = { onToggleComplete(task) },
                    onDelete = { onDelete(task) },
                ) {
                    val actions = rowActions?.invoke(task) ?: TaskRowActions(
                        onEdit = { onOpen(task.id) },
                        onToggleComplete = { onToggleComplete(task) },
                        onDelete = { onDelete(task) },
                    )
                    TaskRowWithMenu(
                        task = task,
                        badges = badges,
                        actions = actions,
                        menuEnabled = menuEnabled,
                        // Here a long press starts the drag to another quadrant, so the "..." button opens the menu
                        longPressOpensMenu = false,
                        modifier = Modifier.dragAndDropSource { _ ->
                            DragAndDropTransferData(ClipData.newPlainText("taskId", task.id))
                        },
                    )
                }
            }
        }
    }
}
