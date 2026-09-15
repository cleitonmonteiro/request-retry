package io.github.cleitonmonteiro.requestretry.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.ui.components.RetryStateScaffold
import io.github.cleitonmonteiro.requestretry.ui.components.ScenarioSelector

@Composable
fun ProfileRoute(
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scenario by viewModel.scenario.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        ScenarioSelector(selected = scenario, onSelect = viewModel::setScenario)
        RetryStateScaffold(
            state = state,
            onRetry = viewModel::retry,
            onLeave = onLeave,
            modifier = Modifier.fillMaxSize(),
        ) { profile -> ProfileCard(profile) }
    }
}

@Composable
private fun ProfileCard(profile: UserProfile) {
    Column {
        Text(text = profile.name)
        Text(text = profile.email, modifier = Modifier.padding(top = 4.dp))
    }
}
