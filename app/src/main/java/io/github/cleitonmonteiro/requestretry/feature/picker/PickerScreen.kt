package io.github.cleitonmonteiro.requestretry.feature.picker

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.domain.model.Action
import io.github.cleitonmonteiro.requestretry.domain.model.Item
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import io.github.cleitonmonteiro.requestretry.retry.RecoveryAction
import io.github.cleitonmonteiro.requestretry.ui.action.ActionButton
import io.github.cleitonmonteiro.requestretry.ui.components.OperationStateScaffold
import io.github.cleitonmonteiro.requestretry.ui.components.ScenarioSelector
import io.github.cleitonmonteiro.requestretry.ui.mvi.CollectEffect

@Composable
fun PickerRoute(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    CollectEffect(viewModel.effects) { effect ->
        when (effect) {
            PickerEffect.NavigateBack -> onLeave()
        }
    }

    PickerScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun PickerScreen(
    state: PickerUiState,
    onIntent: (PickerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScenarioSelector(
            selected = state.scenario,
            onSelect = { onIntent(PickerIntent.SelectScenario(it)) },
        )
        val itemBeingSent = state.selectedItem
        val feedback = state.items as? OperationState.Failed ?: state.send as? OperationState.Failed
        if (feedback != null) {
            OperationStateScaffold(
                state = feedback,
                onRecovery = { recovery ->
                    if (recovery == RecoveryAction.Retry) {
                        onIntent(if (state.items is OperationState.Failed) {
                            PickerIntent.RetryItems
                        } else {
                            PickerIntent.RetrySend
                        })
                    } else {
                        onIntent(PickerIntent.Leave)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {}
        } else {
            OperationStateScaffold(
                state = state.items,
                onRecovery = { recovery ->
                    onIntent(if (recovery == RecoveryAction.Retry) PickerIntent.RetryItems else PickerIntent.Leave)
                },
                modifier = Modifier.weight(1f),
            ) { items ->
                ItemsList(
                    items = items,
                    selectedItemId = itemBeingSent?.id,
                    onSelect = { onIntent(PickerIntent.SelectItem(it)) },
                )
            }
            if (itemBeingSent != null) {
                OperationStateScaffold(
                    state = state.send,
                    onRecovery = { recovery ->
                        onIntent(if (recovery == RecoveryAction.Retry) PickerIntent.RetrySend else PickerIntent.Leave)
                    },
                    modifier = Modifier.weight(1f),
                ) { action -> SendConfirmation(item = itemBeingSent, action = action) }
            }
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
 * SDUI payoff: the client doesn't hardcode "what happens after sending an item," it just renders
 * whatever [ActionButton] the server described.
 */
@Composable
private fun SendConfirmation(item: Item, action: Action, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "Sent \"${item.name}\"")
        Spacer(modifier = Modifier.height(16.dp))
        ActionButton(action = action)
    }
}
