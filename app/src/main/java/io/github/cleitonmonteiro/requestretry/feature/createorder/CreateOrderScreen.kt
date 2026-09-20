package io.github.cleitonmonteiro.requestretry.feature.createorder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.ui.components.RetryStateScaffold
import io.github.cleitonmonteiro.requestretry.ui.components.ScenarioSelector

@Composable
fun CreateOrderRoute(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateOrderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScenarioSelector(selected = uiState.scenario, onSelect = viewModel::setScenario)
        OutlinedTextField(
            value = uiState.input.itemName,
            onValueChange = viewModel::onItemNameChanged,
            label = { Text("Item name") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        OutlinedTextField(
            value = uiState.input.quantity,
            onValueChange = viewModel::onQuantityChanged,
            label = { Text("Quantity") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = uiState.input.customerName,
            onValueChange = viewModel::onCustomerNameChanged,
            label = { Text("Customer name") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Button(onClick = viewModel::submit, modifier = Modifier.padding(16.dp)) {
            Text("Create order")
        }
        if (uiState.hasSubmitted) {
            RetryStateScaffold(
                state = uiState.result,
                onRetry = viewModel::retry,
                onLeave = onLeave,
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
