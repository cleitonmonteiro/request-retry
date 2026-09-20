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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.ui.components.RetryStateScaffold
import io.github.cleitonmonteiro.requestretry.ui.components.ScenarioSelector
import io.github.cleitonmonteiro.requestretry.ui.mvi.CollectEffect

@Composable
fun OrdersRoute(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    CollectEffect(viewModel.effects) { effect ->
        when (effect) {
            OrdersEffect.NavigateBack -> onLeave()
        }
    }

    OrdersScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun OrdersScreen(
    state: OrdersUiState,
    onIntent: (OrdersIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScenarioSelector(
            selected = state.scenario,
            onSelect = { onIntent(OrdersIntent.SelectScenario(it)) },
        )
        RetryStateScaffold(
            state = state.request,
            onRetry = { onIntent(OrdersIntent.Retry) },
            onLeave = { onIntent(OrdersIntent.Leave) },
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
