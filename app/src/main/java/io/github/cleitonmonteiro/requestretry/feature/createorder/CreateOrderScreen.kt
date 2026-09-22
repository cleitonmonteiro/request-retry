package io.github.cleitonmonteiro.requestretry.feature.createorder

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.R
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import io.github.cleitonmonteiro.requestretry.retry.RecoveryAction
import io.github.cleitonmonteiro.requestretry.ui.components.OperationStateScaffold
import io.github.cleitonmonteiro.requestretry.ui.components.ScenarioSelector
import io.github.cleitonmonteiro.requestretry.ui.mvi.CollectEffect

@Composable
fun CreateOrderRoute(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateOrderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    CollectEffect(viewModel.effects) { effect ->
        when (effect) {
            is CreateOrderEffect.ShowOrderCreated -> Toast.makeText(
                context,
                "Order ${effect.orderId} created",
                Toast.LENGTH_SHORT,
            ).show()
            CreateOrderEffect.NavigateBack -> onLeave()
        }
    }

    CreateOrderScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun CreateOrderScreen(
    state: CreateOrderUiState,
    onIntent: (CreateOrderIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScenarioSelector(
            selected = state.scenario,
            onSelect = { onIntent(CreateOrderIntent.SelectScenario(it)) },
        )
        val showingFeedback = state.result is OperationState.Failed
        if (!showingFeedback) OutlinedTextField(
            value = state.input.itemName,
            onValueChange = { onIntent(CreateOrderIntent.ChangeItemName(it)) },
            label = { Text("Item name") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        if (!showingFeedback) OutlinedTextField(
            value = state.input.quantity,
            onValueChange = { onIntent(CreateOrderIntent.ChangeQuantity(it)) },
            label = { Text("Quantity") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (!showingFeedback) OutlinedTextField(
            value = state.input.customerName,
            onValueChange = { onIntent(CreateOrderIntent.ChangeCustomerName(it)) },
            label = { Text("Customer name") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val validationError = state.validationError
        if (!showingFeedback && validationError != null) {
            Text(
                text = stringResource(
                    when (validationError) {
                        OrderValidationError.ITEM_REQUIRED -> R.string.validation_item_required
                        OrderValidationError.QUANTITY_MUST_BE_POSITIVE -> R.string.validation_quantity_positive
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (!showingFeedback) {
            Button(
                onClick = { onIntent(CreateOrderIntent.Submit) },
                modifier = Modifier.padding(16.dp),
            ) {
                Text("Create order")
            }
        }
        if (state.result != OperationState.Idle) {
            OperationStateScaffold(
                state = state.result,
                onRecovery = { recovery ->
                    onIntent(
                        when (recovery) {
                            RecoveryAction.Retry -> CreateOrderIntent.Retry
                            else -> CreateOrderIntent.Leave
                        },
                    )
                },
                modifier = Modifier.weight(1f),
            ) { order -> CreatedOrder(order) }
        }
    }
}

@Composable
private fun CreatedOrder(order: Order) {
    Text(text = "Created ${order.id} — ${order.item}")
    Text(text = "$%.2f".format(order.total))
}
