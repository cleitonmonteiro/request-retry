package io.github.cleitonmonteiro.requestretry.feature.picker

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.domain.model.Action
import io.github.cleitonmonteiro.requestretry.domain.model.ActionType
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
            ) { action -> SendConfirmation(item = itemBeingSent, action = action, onLeave = onLeave) }
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

/**
 * The [Action] the server attached to the send response drives what's shown next — this is the
 * SDUI payoff: the client doesn't hardcode "what happens after sending an item," it branches on
 * [Action.type] and lets the server decide.
 */
@Composable
private fun SendConfirmation(item: Item, action: Action, onLeave: () -> Unit) {
    val context = LocalContext.current

    Column {
        Text(text = "Sent \"${item.name}\"")
        Spacer(modifier = Modifier.height(16.dp))
        when (action.type) {
            ActionType.CLOSE -> {
                Text(text = "Server action: close this screen")
                Button(onClick = onLeave, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Close")
                }
            }

            ActionType.DEEPLINK, ActionType.EXTERNAL_LINK -> {
                val target = action.target
                val label = if (action.type == ActionType.DEEPLINK) "Open deeplink" else "Open link"
                Text(text = "Server action: ${action.type.name.lowercase()} → $target")
                Button(
                    onClick = {
                        if (target == null) return@Button
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                        } catch (e: ActivityNotFoundException) {
                            // No app declares an intent-filter for this target on this device —
                            // real deeplink handling always needs this fallback, not just a demo one.
                            Toast.makeText(context, "No app can handle $target", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(label)
                }
            }
        }
    }
}
