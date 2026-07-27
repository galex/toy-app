package dev.galex.toyapp.automation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Hierarchical id prefix for the composition. Every nested [AutomationContext] concatenates onto
 * its parent's, so an id ends up describing where it lives: "toys" -> "toys_index_2_card".
 */
val LocalAutomationContext = compositionLocalOf { "" }

/** Package prefix Android uses for view ids, so a snapshot looks like a real resource id. */
const val AUTOMATION_ID_PREFIX = "dev.galex.toyapp:id/"

@Composable
fun AutomationContext(context: String, content: @Composable () -> Unit) {
    val parent = LocalAutomationContext.current
    val combined = if (parent.isEmpty()) context else "${parent}_$context"
    CompositionLocalProvider(LocalAutomationContext provides combined, content = content)
}

/**
 * Tags this composable with the current context plus [id], as a Compose `testTag`.
 *
 * The app must set `testTagsAsResourceId = true` once near the root of its composition, otherwise
 * the tag stays invisible to anything reading the accessibility tree.
 */
@Composable
fun Modifier.automationId(id: String): Modifier {
    val context = LocalAutomationContext.current
    val combined = if (context.isEmpty()) id else "${context}_$id"
    return this.testTag("$AUTOMATION_ID_PREFIX$combined")
}

/**
 * Scopes an index into the context, so every row of a list gets ids of its own. This is what stops
 * a list from handing the same id to twenty rows.
 */
@Composable
fun AutomationIndex(index: Int, content: @Composable () -> Unit) =
    AutomationContext("index_$index", content)
