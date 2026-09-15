package io.github.cleitonmonteiro.requestretry.feature.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cleitonmonteiro.requestretry.ui.components.RetryStateScaffold
import io.github.cleitonmonteiro.requestretry.ui.components.ScenarioSelector

@Composable
fun OrdersRoute(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scenario by viewModel.scenario.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScenarioSelector(selected = scenario, onSelect = viewModel::setScenario)
        RetryStateScaffold(
            state = state,
            onRetry = viewModel::retry,
            onLeave = onLeave,
            modifier = Modifier.fillMaxSize(),
        ) { orders -> OrdersList(orders) }
    }
}

@Composable
private fun OrdersList(orders: List<Order>) {
    LazyColumn {
        items(orders) { order ->
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(text = "${order.id} — ${order.item}")
                Text(text = "$%.2f".format(order.total))
            }
        }
    }
}
