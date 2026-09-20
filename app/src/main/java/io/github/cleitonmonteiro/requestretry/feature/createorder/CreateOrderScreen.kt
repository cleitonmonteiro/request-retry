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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import io.github.cleitonmonteiro.requestretry.ui.components.RetryStateScaffold
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
        val showingFeedback = state.result is RetryUiState.Feedback
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
                text = validationError,
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
        if (state.result != RetryUiState.Idle) {
            RetryStateScaffold(
                state = state.result,
                onRetry = { onIntent(CreateOrderIntent.Retry) },
                onLeave = { onIntent(CreateOrderIntent.Leave) },
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
