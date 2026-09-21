package io.github.cleitonmonteiro.requestretry.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetProfileUseCase
import io.github.cleitonmonteiro.requestretry.retry.OperationName
import io.github.cleitonmonteiro.requestretry.retry.OperationSpec
import io.github.cleitonmonteiro.requestretry.retry.RetryControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

/** The screen's single, immutable source of truth — see [feature.picker.PickerUiState] for why. */
data class ProfileUiState(
    val request: RetryUiState<UserProfile>,
    val scenario: Scenario,
)

sealed interface ProfileIntent {
    data object Retry : ProfileIntent
    data object Leave : ProfileIntent
    data class SelectScenario(val scenario: Scenario) : ProfileIntent
}

sealed interface ProfileEffect {
    data object NavigateBack : ProfileEffect
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getProfile: GetProfileUseCase,
    private val scenarios: ScenarioHolder,
    retryControllers: RetryControllerFactory,
) : ViewModel() {

    private val controller = retryControllers.create(
        viewModelScope,
        OperationSpec.read(OperationName.PROFILE),
    ) { getProfile() }
    private val _effects = Channel<ProfileEffect>(Channel.BUFFERED)

    val state: StateFlow<ProfileUiState> = combine(
        controller.state,
        scenarios.scenario,
        ::ProfileUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ProfileUiState(controller.state.value, scenarios.scenario.value),
    )
    val effects = _effects.receiveAsFlow()

    init {
        controller.load()
    }

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Retry -> controller.retry()
            ProfileIntent.Leave -> _effects.trySend(ProfileEffect.NavigateBack)
            is ProfileIntent.SelectScenario -> {
                scenarios.select(intent.scenario)
                controller.load()
            }
        }
    }
}
