package io.github.cleitonmonteiro.requestretry.ui.components

import androidx.annotation.StringRes
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
import androidx.compose.ui.unit.dp
import io.github.cleitonmonteiro.requestretry.R
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import io.github.cleitonmonteiro.requestretry.retry.PublicFailure
import io.github.cleitonmonteiro.requestretry.retry.RecoveryAction

@Composable
fun <T> OperationStateScaffold(
    state: OperationState<T>,
    onRecovery: (RecoveryAction) -> Unit,
    modifier: Modifier = Modifier,
    success: @Composable (T) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            OperationState.Idle -> Unit
            is OperationState.Running -> {
                CircularProgressIndicator(
                    color = if (state.attempt > 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
                if (state.attempt > 1) {
                    Text(
                        text = stringResource(R.string.operation_retrying, state.attempt, state.maxAttempts),
                        modifier = Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            is OperationState.BackingOff -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.tertiary)
                Text(
                    text = stringResource(R.string.operation_backing_off, state.nextAttempt, state.maxAttempts),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            is OperationState.Succeeded -> success(state.data)
            is OperationState.Failed -> FailureContent(
                failure = state.failure,
                recovery = state.recovery,
                attemptsUsed = state.attemptsUsed,
                onRecovery = onRecovery,
            )
            is OperationState.OutcomeUnknown -> {
                Text(
                    text = stringResource(R.string.outcome_unknown_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.outcome_unknown_description),
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { onRecovery(state.recovery) }, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(R.string.action_verify_status))
                }
            }
        }
    }
}

@Composable
private fun FailureContent(
    failure: PublicFailure,
    recovery: RecoveryAction,
    attemptsUsed: Int,
    onRecovery: (RecoveryAction) -> Unit,
) {
    val copy = failure.copyResources()
    Text(
        text = stringResource(copy.title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
    )
    Text(text = stringResource(copy.description), textAlign = TextAlign.Center)
    Text(
        text = stringResource(R.string.operation_attempt, attemptsUsed),
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = { onRecovery(recovery) }, modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(recovery.labelResource()))
    }
}

private data class FailureCopy(@param:StringRes val title: Int, @param:StringRes val description: Int)

private fun PublicFailure.copyResources(): FailureCopy = when (this) {
    PublicFailure.Offline -> FailureCopy(R.string.failure_offline_title, R.string.failure_offline_description)
    PublicFailure.TemporarilyUnavailable -> FailureCopy(R.string.failure_temporary_title, R.string.failure_temporary_description)
    PublicFailure.Validation -> FailureCopy(R.string.failure_validation_title, R.string.failure_validation_description)
    PublicFailure.Local -> FailureCopy(R.string.failure_protocol_title, R.string.failure_protocol_description)
    PublicFailure.Unknown -> FailureCopy(R.string.failure_unknown_title, R.string.failure_unknown_description)
}

@StringRes
private fun RecoveryAction.labelResource(): Int = when (this) {
    RecoveryAction.Retry -> R.string.action_try_again
    RecoveryAction.EditInput -> R.string.action_edit_input
    is RecoveryAction.VerifyStatus -> R.string.action_verify_status
    RecoveryAction.ContactSupport -> R.string.action_contact_support
    RecoveryAction.Leave -> R.string.action_leave
}

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
                    label = { Text(scenario.name.replace('_', ' ').lowercase()) },
                )
            }
        }
    }
}
