package dev.galex.toyapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import dev.galex.toyapp.data.toyById

/** The two screens this demo has. That is the entire navigation graph. */
sealed interface Screen {
    data object Toys : Screen
    data class ToyDetail(val toyId: String) : Screen
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun ToyApp() {
    var screen: Screen by remember { mutableStateOf(Screen.Toys) }

    LaunchedEffect(screen) {
        NavBridge.set(
            when (val current = screen) {
                Screen.Toys -> "Toys"
                is Screen.ToyDetail -> "Toys > ToyDetail(${current.toyId})"
            },
        )
    }

    MaterialTheme {
        Surface(
            // Without this, our testTags never reach the accessibility tree and every tool outside
            // the app is blind to them.
            modifier = Modifier.semantics { testTagsAsResourceId = true },
        ) {
            when (val current = screen) {
                Screen.Toys -> ToyListScreen(
                    onToyClick = { screen = Screen.ToyDetail(it.id) },
                )

                is Screen.ToyDetail -> ToyDetailScreen(
                    toy = toyById(current.toyId),
                    onBack = { screen = Screen.Toys },
                )
            }
        }
    }
}

@Preview
@Composable
private fun ToyAppPreview() {
    ToyApp()
}
