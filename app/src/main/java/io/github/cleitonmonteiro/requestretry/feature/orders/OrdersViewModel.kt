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
import kotlinx.coroutines.flow.StateFlow

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
    val state: StateFlow<RetryUiState<List<Order>>> = controller.state
    val scenario: StateFlow<Scenario> = scenarios.scenario

    init {
        controller.load()
    }

    fun retry() = controller.retry()

    fun setScenario(scenario: Scenario) {
        scenarios.select(scenario)
        controller.load()
    }
}
