package io.github.cleitonmonteiro.requestretry.feature.createorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.usecase.CreateOrderUseCase
import io.github.cleitonmonteiro.requestretry.domain.model.IdempotencyKey
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.retry.OperationControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationName
import io.github.cleitonmonteiro.requestretry.retry.OperationProfiles
import io.github.cleitonmonteiro.requestretry.retry.OperationSpecFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Live form state, kept separate from [NewOrderRequest]: [quantity] stays a String so the field can be transiently empty while typing. */
data class OrderFormInput(
    val itemName: String = "",
    val quantity: String = "1",
    val customerName: String = "",
)

/** The screen's single, immutable source of truth. */
data class CreateOrderUiState(
    val input: OrderFormInput,
    val result: OperationState<Order>,
    val scenario: Scenario,
    val validationError: OrderValidationError?,
)

/** Validation failures that can be shown next to the editable order fields. */
enum class OrderValidationError { ITEM_REQUIRED, QUANTITY_MUST_BE_POSITIVE }

/** All user actions accepted by the create-order feature. */
sealed interface CreateOrderIntent {
    data object Submit : CreateOrderIntent
    data object Retry : CreateOrderIntent
    data object Leave : CreateOrderIntent
    data class ChangeItemName(val value: String) : CreateOrderIntent
    data class ChangeQuantity(val value: String) : CreateOrderIntent
    data class ChangeCustomerName(val value: String) : CreateOrderIntent
    data class SelectScenario(val scenario: Scenario) : CreateOrderIntent
}

/** One-off effects emitted after an order result or a leave request. */
sealed interface CreateOrderEffect {
    data class ShowOrderCreated(val orderId: String) : CreateOrderEffect
    data object NavigateBack : CreateOrderEffect
}

/**
 * Demonstrates a request built from multiple form fields, passed through the layers as one data
 * class ([NewOrderRequest]) rather than loose primitives. [controller] captures the submitted
 * request in an immutable session — never [_formInput] directly — so [CreateOrderIntent.Retry]
 * resends exactly what was submitted even if the form keeps changing afterward.
 */
@HiltViewModel
class CreateOrderViewModel @Inject constructor(
    private val createOrder: CreateOrderUseCase,
    private val scenarios: ScenarioHolder,
    operationControllers: OperationControllerFactory,
) : ViewModel() {
    private val _formInput = MutableStateFlow(OrderFormInput())
    private val _validationError = MutableStateFlow<OrderValidationError?>(null)
    private val controller = operationControllers.create(
        scope = viewModelScope,
        specFactory = OperationSpecFactory<NewOrderRequest> {
            OperationProfiles.foreground(name = OperationName("create_order"))
        },
    ) { request, _ -> createOrder(request).single() }
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
                .filterIsInstance<OperationState.Succeeded<Order>>()
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
            input.itemName.isBlank() -> OrderValidationError.ITEM_REQUIRED
            quantity == null || quantity <= 0 -> OrderValidationError.QUANTITY_MUST_BE_POSITIVE
            else -> null
        }
        if (error != null) {
            _validationError.value = error
            return
        }
        _validationError.value = null
        val request = NewOrderRequest(
            itemName = input.itemName.trim(),
            quantity = requireNotNull(quantity),
            customerName = input.customerName.trim(),
            operationId = OperationId.random(),
            idempotencyKey = IdempotencyKey.random(),
        )
        controller.start(request)
    }
}
