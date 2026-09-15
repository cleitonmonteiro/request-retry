package io.github.cleitonmonteiro.requestretry.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cleitonmonteiro.requestretry.data.FakeApi
import io.github.cleitonmonteiro.requestretry.data.Scenario
import io.github.cleitonmonteiro.requestretry.retry.RetryController
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProfileViewModel(
    private val api: FakeApi<UserProfile> = FakeApi { sampleProfile },
) : ViewModel() {

    private val controller = RetryController(scope = viewModelScope, apiCall = api)
    val state: StateFlow<RetryUiState<UserProfile>> = controller.state

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
