package io.github.cleitonmonteiro.requestretry.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.cleitonmonteiro.requestretry.R
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.retry.PublicFailure
import io.github.cleitonmonteiro.requestretry.retry.RecoveryAction
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import io.github.cleitonmonteiro.requestretry.ui.theme.RequestRetryTheme

/**
 * Renders the Loading, BackingOff and Feedback states shared by every retry-backed screen,
 * delegating only the Success case to the caller. This is the UI half of the retry-core reuse.
 *
 * [onRetry] fires when the rendered [RetryUiState.Feedback.recovery] is [RecoveryAction.Retry];
 * every other recovery action falls back to [onLeave] — none of them make sense to keep the user
 * on this screen for in a demo app with no auth flow or editable-in-place form.
 */
@Composable
fun <T> RetryStateScaffold(
    state: RetryUiState<T>,
    onRetry: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    success: @Composable (T) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            RetryUiState.Idle -> Unit

            is RetryUiState.Loading -> {
                if (state.attempt > 1) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = stringResource(R.string.retry_loading_attempt, state.attempt, state.maxAttempts),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            is RetryUiState.BackingOff -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                Text(
                    text = stringResource(
                        R.string.retry_backing_off,
                        state.secondsRemaining,
                        state.nextAttempt,
                        state.maxAttempts,
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            is RetryUiState.Success -> success(state.data)

            is RetryUiState.Feedback -> {
                Text(
                    text = state.failure.title(),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    // A server-provided message (currently only for a 422) takes priority over
                    // the generic local copy — see RetryUiState.Feedback.serverMessage's KDoc.
                    text = state.serverMessage ?: state.failure.description(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.retry_attempt_count, state.attemptsUsed, state.maxAttempts),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = if (state.recovery == RecoveryAction.Retry) onRetry else onLeave,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(state.recovery.label())
                }
            }
        }
    }
}

/** Lets a demo screen be driven into any [Scenario] on demand. */
@Composable
fun ScenarioSelector(
    selected: Scenario,
    onSelect: (Scenario) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Scenario", style = MaterialTheme.typography.labelLarge)
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Scenario.entries.forEach { scenario ->
                FilterChip(
                    selected = scenario == selected,
                    onClick = { onSelect(scenario) },
                    label = { Text(scenario.name) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RetryStateScaffoldLoadingPreview() {
    RequestRetryTheme {
        RetryStateScaffold<String>(
            state = RetryUiState.Loading(attempt = 1, maxAttempts = 3),
            onRetry = {},
            onLeave = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun RetryStateScaffoldRetryLoadingPreview() {
    RequestRetryTheme {
        RetryStateScaffold<String>(
            state = RetryUiState.Loading(attempt = 2, maxAttempts = 3),
            onRetry = {},
            onLeave = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun RetryStateScaffoldBackingOffPreview() {
    RequestRetryTheme {
        RetryStateScaffold<String>(
            state = RetryUiState.BackingOff(
                nextAttempt = 2,
                maxAttempts = 3,
                secondsRemaining = 3,
                reason = io.github.cleitonmonteiro.requestretry.retry.RetryReason.BACKOFF,
            ),
            onRetry = {},
            onLeave = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun RetryStateScaffoldSuccessPreview() {
    RequestRetryTheme {
        RetryStateScaffold(
            state = RetryUiState.Success("Loaded!"),
            onRetry = {},
            onLeave = {},
        ) { data -> Text(text = data) }
    }
}

@Preview(showBackground = true)
@Composable
private fun RetryStateScaffoldFeedbackPreview() {
    RequestRetryTheme {
        RetryStateScaffold<String>(
            state = RetryUiState.Feedback(
                failure = PublicFailure.CONNECTION,
                recovery = RecoveryAction.Retry,
                attemptsUsed = 1,
                maxAttempts = 3,
            ),
            onRetry = {},
            onLeave = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun RetryStateScaffoldExhaustedPreview() {
    RequestRetryTheme {
        RetryStateScaffold<String>(
            state = RetryUiState.Feedback(
                failure = PublicFailure.CONNECTION,
                recovery = RecoveryAction.GoBack,
                attemptsUsed = 3,
                maxAttempts = 3,
            ),
            onRetry = {},
            onLeave = {},
        ) {}
    }
}
