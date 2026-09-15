package io.github.cleitonmonteiro.requestretry.feature.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cleitonmonteiro.requestretry.data.FakeApi
import io.github.cleitonmonteiro.requestretry.data.Scenario
import io.github.cleitonmonteiro.requestretry.retry.RetryController
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Same shape as [io.github.cleitonmonteiro.requestretry.feature.profile.ProfileViewModel],
 * over a different payload — that's the point: [RetryController] is what's reused, not
 * this class.
 */
class OrdersViewModel(
    private val api: FakeApi<List<Order>> = FakeApi { sampleOrders },
) : ViewModel() {

    private val controller = RetryController(scope = viewModelScope, apiCall = api)
    val state: StateFlow<RetryUiState<List<Order>>> = controller.state

    private val _scenario = MutableStateFlow(api.scenario)
    val scenario: StateFlow<Scenario> = _scenario.asStateFlow()

    init {
        controller.load()
    }

    fun retry() = controller.retry()

    fun setScenario(scenario: Scenario) {
        api.scenario = scenario
        _scenario.value = scenario
        controller.load()
    }
}
