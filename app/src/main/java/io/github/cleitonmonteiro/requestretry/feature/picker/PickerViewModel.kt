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
import io.github.cleitonmonteiro.requestretry.retry.RetryControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The screen's single, immutable source of truth — strict MVI: one [PickerViewModel.state]
 * StateFlow, combined from every independent piece that makes it up, rather than several
 * StateFlows a caller could read out of sync with one another.
 */
data class PickerUiState(
    val items: RetryUiState<List<Item>>,
    val send: RetryUiState<Action>,
    val selectedItem: Item?,
    val scenario: Scenario,
)

/**
 * Two independent requests on one screen: [itemsController] loads automatically like every
 * other screen, but [sendController] is built up front and left unstarted until [selectItem]
 * calls its `load()` for the first time — a controller starts in [RetryUiState.Idle], so the UI
 * can safely render it before then. Their two [RetryUiState]s
 * are folded into one [PickerUiState] alongside the selection and scenario, so the screen still
 * has a single source of truth even though it's driven by four independent flows underneath.
 */
@HiltViewModel
class PickerViewModel @Inject constructor(
    private val getItems: GetItemsUseCase,
    private val sendItem: SendItemUseCase,
    private val scenarios: ScenarioHolder,
    retryControllers: RetryControllerFactory,
) : ViewModel() {

    private var itemToSend: Item? = null

    private val itemsController = retryControllers.create(viewModelScope) { getItems() }
    private val sendController = retryControllers.create(viewModelScope) {
        sendItem(requireNotNull(itemToSend))
    }
    private val _selectedItem = MutableStateFlow<Item?>(null)

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

    init {
        itemsController.load()
    }

    fun retryItems() = itemsController.retry()

    fun retrySend() = sendController.retry()

    fun selectItem(item: Item) {
        itemToSend = item
        _selectedItem.value = item
        sendController.load() // fires (or re-fires, for a different item) immediately on selection
    }

    fun setScenario(scenario: Scenario) {
        scenarios.select(scenario)
        itemsController.load()
        // sendController is deliberately left alone: it may not have started yet, and if it
        // has, a scenario change shouldn't silently resurrect a past selection's send.
    }
}
