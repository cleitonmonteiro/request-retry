package io.github.cleitonmonteiro.requestretry.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import io.github.cleitonmonteiro.requestretry.retry.RecoveryAction
import io.github.cleitonmonteiro.requestretry.ui.components.OperationStateScaffold
import io.github.cleitonmonteiro.requestretry.ui.mvi.CollectEffect

@Composable
fun ProfileRoute(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    CollectEffect(viewModel.effects) { effect ->
        when (effect) {
            ProfileEffect.NavigateBack -> onLeave()
        }
    }

    ProfileScreen(
        state = uiState,
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}

@Composable
private fun ProfileScreen(
    state: OperationState<UserProfile>,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OperationStateScaffold(
        state = state,
        onRecovery = { recovery ->
            onIntent(if (recovery == RecoveryAction.Retry) ProfileIntent.Retry else ProfileIntent.Leave)
        },
        modifier = modifier.fillMaxSize(),
    ) { profile -> ProfileCard(profile) }
}

@Composable
private fun ProfileCard(profile: UserProfile) {
    Column {
        Text(text = profile.name)
        Text(text = profile.email, modifier = Modifier.padding(top = 4.dp))
    }
}
