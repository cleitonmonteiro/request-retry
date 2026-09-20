package io.github.cleitonmonteiro.requestretry.feature.createorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.usecase.CreateOrderUseCase
import io.github.cleitonmonteiro.requestretry.retry.RetryControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Live form state, kept separate from [NewOrderRequest]: [quantity] stays a String so the field can be transiently empty while typing. */
data class OrderFormInput(
    val itemName: String = "",
    val quantity: String = "1",
    val customerName: String = "",
)

/** The screen's single, immutable source of truth — see [feature.picker.PickerUiState] for why. */
data class CreateOrderUiState(
    val input: OrderFormInput,
    val hasSubmitted: Boolean,
    val result: RetryUiState<Order>,
    val scenario: Scenario,
)

/**
 * Demonstrates a request built from multiple form fields, passed through the layers as one data
 * class ([NewOrderRequest]) rather than loose primitives. [lastSubmittedRequest] is captured at
 * submit time and is what the [controller]'s ApiCall reads — never [_formInput] directly — so
 * [retry] resends exactly what was submitted even if the form keeps changing afterward.
 */
@HiltViewModel
class CreateOrderViewModel @Inject constructor(
    private val createOrder: CreateOrderUseCase,
    private val scenarios: ScenarioHolder,
    retryControllers: RetryControllerFactory,
) : ViewModel() {

    private var lastSubmittedRequest = NewOrderRequest(itemName = "", quantity = 1, customerName = "")

    private val _formInput = MutableStateFlow(OrderFormInput())
    private val _hasSubmitted = MutableStateFlow(false)
    private val controller = retryControllers.create(viewModelScope) { createOrder(lastSubmittedRequest) }

    val state: StateFlow<CreateOrderUiState> = combine(
        _formInput,
        _hasSubmitted,
        controller.state,
        scenarios.scenario,
        ::CreateOrderUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CreateOrderUiState(
            input = _formInput.value,
            hasSubmitted = _hasSubmitted.value,
            result = controller.state.value,
            scenario = scenarios.scenario.value,
        ),
    )

    fun onItemNameChanged(value: String) {
        _formInput.update { it.copy(itemName = value) }
    }

    fun onQuantityChanged(value: String) {
        _formInput.update { it.copy(quantity = value.filter(Char::isDigit)) }
    }

    fun onCustomerNameChanged(value: String) {
        _formInput.update { it.copy(customerName = value) }
    }

    fun submit() {
        val input = _formInput.value
        val quantity = input.quantity.toIntOrNull() ?: return
        if (input.itemName.isBlank()) return
        lastSubmittedRequest = NewOrderRequest(
            itemName = input.itemName.trim(),
            quantity = quantity,
            customerName = input.customerName.trim(),
        )
        _hasSubmitted.value = true
        controller.load()
    }

    fun retry() = controller.retry()

    fun setScenario(scenario: Scenario) {
        scenarios.select(scenario)
        // Same rule as Picker: don't resurrect a submit that never happened.
        if (_hasSubmitted.value) controller.load()
    }
}
