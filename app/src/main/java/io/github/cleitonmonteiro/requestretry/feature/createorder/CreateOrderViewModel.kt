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
import java.util.UUID
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
    val validationError: String?,
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
    private val _validationError = MutableStateFlow<String?>(null)
    private val controller = retryControllers.create(viewModelScope) { createOrder(lastSubmittedRequest) }

    val state: StateFlow<CreateOrderUiState> = combine(
        _formInput,
        _hasSubmitted,
        controller.state,
        scenarios.scenario,
        _validationError,
        ::CreateOrderUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CreateOrderUiState(
            input = _formInput.value,
            hasSubmitted = _hasSubmitted.value,
            result = controller.state.value,
            scenario = scenarios.scenario.value,
            validationError = _validationError.value,
        ),
    )

    fun onItemNameChanged(value: String) {
        _formInput.update { it.copy(itemName = value) }
        _validationError.value = null
    }

    fun onQuantityChanged(value: String) {
        _formInput.update { it.copy(quantity = value.filter(Char::isDigit)) }
        _validationError.value = null
    }

    fun onCustomerNameChanged(value: String) {
        _formInput.update { it.copy(customerName = value) }
    }

    fun submit() {
        val input = _formInput.value
        val quantity = input.quantity.toIntOrNull()
        val error = when {
            input.itemName.isBlank() -> "Item name is required"
            quantity == null || quantity <= 0 -> "Quantity must be a positive number"
            else -> null
        }
        if (error != null) {
            _validationError.value = error
            return
        }
        _validationError.value = null
        lastSubmittedRequest = NewOrderRequest(
            itemName = input.itemName.trim(),
            quantity = requireNotNull(quantity),
            customerName = input.customerName.trim(),
            idempotencyKey = UUID.randomUUID().toString(),
        )
        _hasSubmitted.value = true
        controller.load()
    }

    fun retry() = controller.retry()

    fun setScenario(scenario: Scenario) {
        scenarios.select(scenario)
        // Unlike Picker's itemsController (a read-only GET, safe to replay), this screen's only
        // controller wraps a non-idempotent POST /orders — reloading it here would silently
        // resend lastSubmittedRequest and create a duplicate order. A scenario change only
        // affects the *next* submit; it never replays the last one, submitted or not.
    }
}
