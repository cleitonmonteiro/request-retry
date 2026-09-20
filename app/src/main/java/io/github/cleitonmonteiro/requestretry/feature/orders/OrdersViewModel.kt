package io.github.cleitonmonteiro.requestretry.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetOrdersUseCase
import io.github.cleitonmonteiro.requestretry.retry.OperationControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationName
import io.github.cleitonmonteiro.requestretry.retry.OperationProfiles
import io.github.cleitonmonteiro.requestretry.retry.OperationSpecFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.stateIn

/** The screen's single, immutable source of truth — see [feature.picker.PickerUiState] for why. */
data class OrdersUiState(
    val request: OperationState<List<Order>>,
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
 * over a different payload — the payload-agnostic operation pipeline is reused, not this class.
 */
@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val getOrders: GetOrdersUseCase,
    private val scenarios: ScenarioHolder,
    operationControllers: OperationControllerFactory,
) : ViewModel() {

    private val controller = operationControllers.create(
        scope = viewModelScope,
        specFactory = OperationSpecFactory<Unit> { OperationProfiles.foregroundRead(OperationName.ORDERS_READ) },
    ) { _, _ -> getOrders().single() }
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
        controller.start(Unit)
    }

    fun onIntent(intent: OrdersIntent) {
        when (intent) {
            OrdersIntent.Retry -> controller.retry()
            OrdersIntent.Leave -> _effects.trySend(OrdersEffect.NavigateBack)
            is OrdersIntent.SelectScenario -> {
                scenarios.select(intent.scenario)
                controller.start(Unit)
            }
        }
    }
}
