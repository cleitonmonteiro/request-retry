package io.github.cleitonmonteiro.requestretry.feature.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.Action
import io.github.cleitonmonteiro.requestretry.domain.model.Item
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetItemsUseCase
import io.github.cleitonmonteiro.requestretry.domain.usecase.SendItemUseCase
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.stateIn

/**
 * The screen's single, immutable source of truth — strict MVI: one [PickerViewModel.state]
 * StateFlow, combined from every independent piece that makes it up, rather than several
 * StateFlows a caller could read out of sync with one another.
 */
data class PickerUiState(
    val items: OperationState<List<Item>>,
    val send: OperationState<Action>,
    val selectedItem: Item?,
    val scenario: Scenario,
)

sealed interface PickerIntent {
    data object RetryItems : PickerIntent
    data object RetrySend : PickerIntent
    data object Leave : PickerIntent
    data class SelectItem(val item: Item) : PickerIntent
    data class SelectScenario(val scenario: Scenario) : PickerIntent
}

sealed interface PickerEffect {
    data object NavigateBack : PickerEffect
}

/**
 * Two independent requests on one screen: [itemsController] loads automatically like every
 * other screen, but [sendController] is built up front and left unstarted until
 * [PickerIntent.SelectItem] starts it for the first time — a controller starts in
 * [OperationState.Idle], so the UI can safely render it before then. Their two operation states
 * are folded into one [PickerUiState] alongside the selection and scenario, so the screen still
 * has a single source of truth even though it's driven by four independent flows underneath.
 */
@HiltViewModel
class PickerViewModel @Inject constructor(
    private val getItems: GetItemsUseCase,
    private val sendItem: SendItemUseCase,
    private val scenarios: ScenarioHolder,
    operationControllers: OperationControllerFactory,
) : ViewModel() {
    private val itemsController = operationControllers.create(
        scope = viewModelScope,
        specFactory = OperationSpecFactory<Unit> { OperationProfiles.foregroundRead(OperationName.ITEMS_READ) },
    ) { _, _ -> getItems().single() }
    private val sendController = operationControllers.create(
        scope = viewModelScope,
        specFactory = OperationSpecFactory<Item> { OperationProfiles.foregroundUnsafeCommand(OperationName.ITEM_SEND) },
    ) { item, _ -> sendItem(item).single() }
    private val _selectedItem = MutableStateFlow<Item?>(null)
    private val _effects = Channel<PickerEffect>(Channel.BUFFERED)

    val state: StateFlow<PickerUiState> = combine(
        itemsController.state,
        sendController.state,
        _selectedItem,
        scenarios.scenario,
        ::PickerUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = PickerUiState(
            items = itemsController.state.value,
            send = sendController.state.value,
            selectedItem = _selectedItem.value,
            scenario = scenarios.scenario.value,
        ),
    )
    val effects = _effects.receiveAsFlow()

    init {
        itemsController.start(Unit)
    }

    fun onIntent(intent: PickerIntent) {
        when (intent) {
            PickerIntent.RetryItems -> itemsController.retry()
            PickerIntent.RetrySend -> sendController.retry()
            PickerIntent.Leave -> _effects.trySend(PickerEffect.NavigateBack)
            is PickerIntent.SelectItem -> {
                _selectedItem.value = intent.item
                sendController.start(intent.item)
            }
            is PickerIntent.SelectScenario -> {
                scenarios.select(intent.scenario)
                itemsController.start(Unit)
                // The send controller is deliberately left alone: it may not have started yet,
                // and a scenario change must not silently replay a past selection's send.
            }
        }
    }
}
