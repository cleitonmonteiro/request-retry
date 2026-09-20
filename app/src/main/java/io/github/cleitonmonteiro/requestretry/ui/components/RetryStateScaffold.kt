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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import io.github.cleitonmonteiro.requestretry.ui.theme.RequestRetryTheme

/**
 * Renders the Loading and Feedback states shared by every retry-backed screen, delegating
 * only the Success case to the caller. This is the UI half of the retry-core reuse.
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
                if (state.retryAttempt != null) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        text = "Retrying — attempt ${state.retryAttempt} of ${state.maxRetries}",
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    CircularProgressIndicator()
                }
            }

            is RetryUiState.Success -> success(state.data)

            is RetryUiState.Feedback -> {
                Text(
                    text = state.message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Attempt ${state.retriesUsed} of ${state.maxRetries} retries used",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.canRetry) {
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Retry (${state.retriesUsed} of ${state.maxRetries})")
                    }
                } else {
                    Button(onClick = onLeave, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Leave")
                    }
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
            state = RetryUiState.Loading(),
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
            state = RetryUiState.Loading(retryAttempt = 1, maxRetries = 3),
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
                message = "Request failed",
                retriesUsed = 1,
                maxRetries = 3,
                canRetry = true,
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
                message = "Request failed",
                retriesUsed = 3,
                maxRetries = 3,
                canRetry = false,
            ),
            onRetry = {},
            onLeave = {},
        ) {}
    }
}
