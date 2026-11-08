package dev.vinny.gsd.matrix

import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A card's two icon buttons must carry their names on the node that takes the click, because that is the node
 * TalkBack focuses. Compose's own semantics tree was right all along; a live TalkBack read still spoke both as
 * just "Button", because the Android accessibility tree split the name onto a sibling. So this test reads the
 * accessibility tree a screen reader sees, not Compose's.
 */
class CardButtonLabelsTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private val task = Task(id = "task-under-test-00000", title = "Renew passport", urgent = false, important = false, createdAt = t0, updatedAt = t0)

    private fun show(task: Task) {
        rule.setContent {
            GsdTheme {
                TaskCard(task = task, onToggleComplete = {}, onClick = {}, onOpenMenu = {})
            }
        }
        rule.waitForIdle()
    }

    /** Every node in the active window's accessibility tree that carries [label] as its content description. */
    private fun named(label: String): List<AccessibilityNodeInfo> {
        // The window can take a moment to become the active one, so ask for its tree for up to two seconds
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        var root: AccessibilityNodeInfo? = null
        repeat(20) { if (root == null) { root = automation.rootInActiveWindow; if (root == null) Thread.sleep(100) } }
        checkNotNull(root) { "no active window" }
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) {
            if (node.contentDescription?.toString() == label) found += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root!!)
        return found
    }

    @Test
    fun theOptionsButtonNamesTheNodeThatTakesTheClick() {
        show(task)
        val nodes = named("Task options")
        assertEquals(1, nodes.size, "one node carries the name")
        assertTrue(nodes.single().isClickable && nodes.single().isFocusable, "and it takes the click and the focus")
    }

    @Test
    fun theCompleteButtonNamesTheNodeThatTakesTheClick() {
        show(task)
        val nodes = named("Mark complete")
        assertEquals(1, nodes.size, "one node carries the name")
        assertTrue(nodes.single().isClickable && nodes.single().isFocusable, "and it takes the click and the focus")
    }

    @Test
    fun aCompletedCardOffersMarkIncomplete() {
        show(task.copy(completed = true))
        val nodes = named("Mark incomplete")
        assertEquals(1, nodes.size)
        assertTrue(nodes.single().isClickable && nodes.single().isFocusable)
    }
}
