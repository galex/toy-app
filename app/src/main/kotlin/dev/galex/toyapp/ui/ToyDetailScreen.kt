package dev.galex.toyapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.galex.toyapp.automation.AutomationContext
import dev.galex.toyapp.automation.automationId
import dev.galex.toyapp.data.Toy
import dev.galex.toyapp.data.toys

@Composable
fun ToyDetailScreen(toy: Toy?, onBack: () -> Unit) {
    AutomationContext("toy_detail") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = toy?.name ?: "Unknown toy",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.automationId("name"),
            )
            if (toy != null) {
                Text(
                    text = "${toy.category} · ages ${toy.ageRange} · ${toy.pieces} piece(s)",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.automationId("meta"),
                )
                Text(
                    text = toy.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.automationId("description"),
                )
            }
            Button(
                onClick = onBack,
                modifier = Modifier.automationId("back_button"),
            ) {
                Text("Back to toys")
            }
        }
    }
}

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun ToyDetailScreenPreview() {
    MaterialTheme { ToyDetailScreen(toy = toys.first(), onBack = {}) }
}
