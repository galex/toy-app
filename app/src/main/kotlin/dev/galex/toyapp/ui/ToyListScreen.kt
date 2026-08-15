package dev.galex.toyapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.galex.toyapp.automation.AutomationContext
import dev.galex.toyapp.automation.AutomationIndex
import dev.galex.toyapp.automation.automationId
import dev.galex.toyapp.data.Toy
import dev.galex.toyapp.data.toys

@Composable
fun ToyListScreen(onToyClick: (Toy) -> Unit) {
    // Everything below this scope gets ids starting with "toys_".
    AutomationContext(ToysIds.Context) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Toys",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(24.dp)
                    .automationId(ToysIds.TitleSegment),
            )
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.automationId(ToysIds.ListSegment),
            ) {
                itemsIndexed(toys) { index, toy ->
                    // The index scope is what stops six rows from sharing one id.
                    AutomationIndex(index) {
                        ToyCard(toy = toy, onClick = { onToyClick(toy) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ToyCard(toy: Toy, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .automationId(ToysIds.CardSegment),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = toy.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.automationId(ToysIds.NameSegment),
            )
            Text(
                text = "${toy.category} · ages ${toy.ageRange}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.automationId(ToysIds.SubtitleSegment),
            )
        }
    }
}

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun ToyListScreenPreview() {
    MaterialTheme { ToyListScreen(onToyClick = {}) }
}
