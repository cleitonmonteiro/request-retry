package io.github.cleitonmonteiro.requestretry.feature.createorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.usecase.CreateOrderUseCase
import io.github.cleitonmonteiro.requestretry.retry.OperationName
import io.github.cleitonmonteiro.requestretry.retry.OperationSpec
import io.github.cleitonmonteiro.requestretry.retry.RetryControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Live form state, kept separate from [NewOrderRequest]: [quantity] stays a String so the field can be transiently empty while typing. */
data class OrderFormInput(
    val itemName: String = "",
    val quantity: String = "1",
    val customerName: String = "",
)

/** The screen's single, immutable source of truth — see [feature.picker.PickerUiState] for why. */
data class CreateOrderUiState(
    val input: OrderFormInput,
    val result: RetryUiState<Order>,
    val scenario: Scenario,
    val validationError: String?,
)

sealed interface CreateOrderIntent {
    data object Submit : CreateOrderIntent
    data object Retry : CreateOrderIntent
    data object Leave : CreateOrderIntent
    data class ChangeItemName(val value: String) : CreateOrderIntent
    data class ChangeQuantity(val value: String) : CreateOrderIntent
    data class ChangeCustomerName(val value: String) : CreateOrderIntent
    data class SelectScenario(val scenario: Scenario) : CreateOrderIntent
}

sealed interface CreateOrderEffect {
    data class ShowOrderCreated(val orderId: String) : CreateOrderEffect
    data object NavigateBack : CreateOrderEffect
}

/**
 * Demonstrates a request built from multiple form fields, passed through the layers as one data
 * class ([NewOrderRequest]) rather than loose primitives. [lastSubmittedRequest] is captured at
 * submit time and is what the [controller]'s ApiCall reads — never [_formInput] directly — so
 * [CreateOrderIntent.Retry] resends exactly what was submitted even if the form keeps changing
 * afterward.
 */
@HiltViewModel
class CreateOrderViewModel @Inject constructor(
    private val createOrder: CreateOrderUseCase,
    private val scenarios: ScenarioHolder,
    retryControllers: RetryControllerFactory,
) : ViewModel() {

    private var lastSubmittedRequest = NewOrderRequest(itemName = "", quantity = 1, customerName = "")

    private val _formInput = MutableStateFlow(OrderFormInput())
    private val _validationError = MutableStateFlow<String?>(null)
    // A command's failure whose outcome is uncertain is always terminal — see
    // DefaultRetryDecider — even though this request already carries an Idempotency-Key (kept
    // as-is per the plan's §1.1 decision not to evolve it further); the retry decision here
    // doesn't distinguish commands by idempotency.
    private val controller = retryControllers.create(
        viewModelScope,
        OperationSpec.command(OperationName.CREATE_ORDER),
    ) { createOrder(lastSubmittedRequest) }
    private val _effects = Channel<CreateOrderEffect>(Channel.BUFFERED)

    val state: StateFlow<CreateOrderUiState> = combine(
        _formInput,
        controller.state,
        scenarios.scenario,
        _validationError,
        ::CreateOrderUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CreateOrderUiState(
            input = _formInput.value,
            result = controller.state.value,
            scenario = scenarios.scenario.value,
            validationError = _validationError.value,
        ),
    )
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            controller.state
                .filterIsInstance<RetryUiState.Success<Order>>()
                .collect { _effects.send(CreateOrderEffect.ShowOrderCreated(it.data.id)) }
        }
    }

    fun onIntent(intent: CreateOrderIntent) {
        when (intent) {
            CreateOrderIntent.Submit -> submit()
            CreateOrderIntent.Retry -> controller.retry()
            CreateOrderIntent.Leave -> _effects.trySend(CreateOrderEffect.NavigateBack)
            is CreateOrderIntent.ChangeItemName -> {
                _formInput.update { it.copy(itemName = intent.value) }
                _validationError.value = null
            }
            is CreateOrderIntent.ChangeQuantity -> {
                _formInput.update { it.copy(quantity = intent.value.filter(Char::isDigit)) }
                _validationError.value = null
            }
            is CreateOrderIntent.ChangeCustomerName -> {
                _formInput.update { it.copy(customerName = intent.value) }
            }
            is CreateOrderIntent.SelectScenario -> scenarios.select(intent.scenario)
        }
    }

    private fun submit() {
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
        controller.load()
    }
}
