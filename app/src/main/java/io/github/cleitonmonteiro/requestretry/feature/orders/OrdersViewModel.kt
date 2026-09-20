package io.github.cleitonmonteiro.requestretry.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetOrdersUseCase
import io.github.cleitonmonteiro.requestretry.retry.RetryControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** The screen's single, immutable source of truth — see [feature.picker.PickerUiState] for why. */
data class OrdersUiState(
    val request: RetryUiState<List<Order>>,
    val scenario: Scenario,
)

sealed interface OrdersIntent {
    data object Retry : OrdersIntent
    data object Leave : OrdersIntent
    data class SelectScenario(val scenario: Scenario) : OrdersIntent
}

sealed interface OrdersEffect {
    data object NavigateBack : OrdersEffect
}

/**
 * Same shape as [io.github.cleitonmonteiro.requestretry.feature.profile.ProfileViewModel],
 * over a different payload — that's the point: [io.github.cleitonmonteiro.requestretry.retry.RetryController]
 * is what's reused, not this class.
 */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val getOrders: GetOrdersUseCase,
    private val scenarios: ScenarioHolder,
    retryControllers: RetryControllerFactory,
) : ViewModel() {

    private val controller = retryControllers.create(viewModelScope) { getOrders() }
    private val _effects = Channel<OrdersEffect>(Channel.BUFFERED)

    val state: StateFlow<OrdersUiState> = combine(
        controller.state,
        scenarios.scenario,
        ::OrdersUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OrdersUiState(controller.state.value, scenarios.scenario.value),
    )
    val effects = _effects.receiveAsFlow()

    init {
        controller.load()
    }

    fun onIntent(intent: OrdersIntent) {
        when (intent) {
            OrdersIntent.Retry -> controller.retry()
            OrdersIntent.Leave -> _effects.trySend(OrdersEffect.NavigateBack)
            is OrdersIntent.SelectScenario -> {
                scenarios.select(intent.scenario)
                controller.load()
            }
        }
    }
}
