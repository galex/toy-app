package dev.galex.toyapp.probe

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Dumps what is on screen straight from Compose's own semantics tree, the same tree Compose UI
 * tests read, reached through the backing view's [ViewRootForTest].
 *
 * We deliberately do NOT walk [View.createAccessibilityNodeInfo]: outside a real
 * AccessibilityService that root node comes back un-sealed, so reading its bounds throws
 * "not sealed instance", and its virtual children never resolve without an accessibility
 * connection.
 *
 * This is a same-window dump. A dialog or a bottom sheet that opens its own window is a separate
 * composition and will not appear here, which is what a debug-only AccessibilityService would be
 * for.
 */
class SemanticsElementSource(
    private val activityTracker: CurrentActivityTracker,
) : ElementSource {

    override fun snapshot(): List<UiElement> {
        val decorView = activityTracker.current?.window?.decorView ?: return emptyList()
        val root = findViewRoot(decorView) ?: return emptyList()
        val elements = mutableListOf<UiElement>()
        // The UNMERGED tree, so every automationId keeps its own node and its own bounds instead of
        // being merged away into the nearest clickable ancestor.
        dump(root.semanticsOwner.unmergedRootSemanticsNode, elements)
        return elements
    }

    private fun findViewRoot(view: View): ViewRootForTest? {
        if (view is ViewRootForTest) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findViewRoot(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun dump(node: SemanticsNode, out: MutableList<UiElement>) {
        val config = node.config
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        // boundsInWindow, because that is the exact space AndroidProbeDriver dispatches its
        // MotionEvents into. Mixing it with boundsInRoot lands every tap next to its target.
        val bounds = node.boundsInWindow
        out.add(
            UiElement(
                id = config.getOrNull(SemanticsProperties.TestTag),
                text = text,
                role = config.getOrNull(SemanticsProperties.Role)?.toString(),
                x = bounds.left,
                y = bounds.top,
                width = bounds.width,
                height = bounds.height,
                clickable = config.getOrNull(SemanticsActions.OnClick) != null,
            ),
        )
        for (child in node.children) dump(child, out)
    }
}
