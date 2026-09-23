package io.github.cleitonmonteiro.requestretry.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetProfileUseCase
import io.github.cleitonmonteiro.requestretry.retry.OperationControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationName
import io.github.cleitonmonteiro.requestretry.retry.OperationSpec
import io.github.cleitonmonteiro.requestretry.retry.OperationSpecFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.single

/** All user actions accepted by the profile feature. */
sealed interface ProfileIntent {
    data object Retry : ProfileIntent
    data object Leave : ProfileIntent
}

/** One-off navigation events emitted by the profile feature. */
sealed interface ProfileEffect {
    data object NavigateBack : ProfileEffect
}

@HiltViewModel
/** Owns profile loading and manual retry commands for the screen. */
class ProfileViewModel @Inject constructor(
    private val getProfile: GetProfileUseCase,
    operationControllers: OperationControllerFactory,
) : ViewModel() {

    private val controller = operationControllers.create(
        scope = viewModelScope,
        specFactory = OperationSpecFactory<Unit> {
            OperationSpec(OperationName("profile_read"))
        },
    ) { _, _ -> getProfile().single() }
    private val _effects = Channel<ProfileEffect>(Channel.BUFFERED)

    val state: StateFlow<OperationState<UserProfile>> = controller.state
    val effects = _effects.receiveAsFlow()

    init {
        controller.start(Unit)
    }

    fun onIntent(intent: ProfileIntent) {
        when (intent) {
            ProfileIntent.Retry -> controller.retry()
            ProfileIntent.Leave -> _effects.trySend(ProfileEffect.NavigateBack)
        }
    }
}
