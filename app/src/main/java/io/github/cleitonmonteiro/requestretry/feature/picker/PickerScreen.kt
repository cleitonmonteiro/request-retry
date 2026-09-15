package io.github.cleitonmonteiro.requestretry.feature.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.domain.model.Item
import io.github.cleitonmonteiro.requestretry.ui.components.RetryStateScaffold
import io.github.cleitonmonteiro.requestretry.ui.components.ScenarioSelector

@Composable
fun PickerRoute(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScenarioSelector(selected = uiState.scenario, onSelect = viewModel::setScenario)
        RetryStateScaffold(
            state = uiState.items,
            onRetry = viewModel::retryItems,
            onLeave = onLeave,
            modifier = Modifier.weight(1f),
        ) { items ->
            ItemsList(items = items, selectedItemId = uiState.selectedItem?.id, onSelect = viewModel::selectItem)
        }

        val itemBeingSent = uiState.selectedItem
        if (itemBeingSent != null) {
            RetryStateScaffold(
                state = uiState.send,
                onRetry = viewModel::retrySend,
                onLeave = onLeave,
                modifier = Modifier.weight(1f),
            ) { SendConfirmation(item = itemBeingSent) }
        }
    }
}

@Composable
private fun ItemsList(
    items: List<Item>,
    selectedItemId: String?,
    onSelect: (Item) -> Unit,
) {
    LazyColumn {
        items(items) { item ->
            val isSelected = item.id == selectedItemId
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) }
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background)
                    .padding(vertical = 12.dp),
            ) {
                Text(text = if (isSelected) "${item.name} ✓" else item.name)
            }
        }
    }
}

@Composable
private fun SendConfirmation(item: Item) {
    Text(text = "Sent \"${item.name}\"")
}
