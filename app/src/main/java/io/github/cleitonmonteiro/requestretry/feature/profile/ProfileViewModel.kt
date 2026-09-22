package io.github.cleitonmonteiro.requestretry.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetProfileUseCase
import io.github.cleitonmonteiro.requestretry.retry.OperationControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationName
import io.github.cleitonmonteiro.requestretry.retry.OperationProfiles
import io.github.cleitonmonteiro.requestretry.retry.OperationSpecFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.stateIn

/** The screen's single, immutable source of truth. */
data class ProfileUiState(
    val request: OperationState<UserProfile>,
    val scenario: Scenario,
)

/** All user actions accepted by the profile feature. */
sealed interface ProfileIntent {
    data object Retry : ProfileIntent
    data object Leave : ProfileIntent
    data class SelectScenario(val scenario: Scenario) : ProfileIntent
}

/** One-off navigation events emitted by the profile feature. */
sealed interface ProfileEffect {
    data object NavigateBack : ProfileEffect
}

@HiltViewModel
/** Owns profile loading, scenario selection, and manual retry commands for the screen. */
class ProfileViewModel @Inject constructor(
    private val getProfile: GetProfileUseCase,
    private val scenarios: ScenarioHolder,
    operationControllers: OperationControllerFactory,
) : ViewModel() {

    private val controller = operationControllers.create(
        scope = viewModelScope,
        specFactory = OperationSpecFactory<Unit> { OperationProfiles.foregroundRead(OperationName.PROFILE_READ) },
    ) { _, _ -> getProfile().single() }
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
        controller.start(Unit)
    }

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Retry -> controller.retry()
            ProfileIntent.Leave -> _effects.trySend(ProfileEffect.NavigateBack)
            is ProfileIntent.SelectScenario -> {
                scenarios.select(intent.scenario)
                controller.start(Unit)
            }
        }
    }
}
