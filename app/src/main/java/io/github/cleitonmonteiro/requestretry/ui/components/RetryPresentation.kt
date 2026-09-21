package io.github.cleitonmonteiro.requestretry.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.cleitonmonteiro.requestretry.R
import io.github.cleitonmonteiro.requestretry.retry.PublicFailure
import io.github.cleitonmonteiro.requestretry.retry.RecoveryAction

/**
 * The only place `retry/`'s failure/recovery types turn into copy — everything here reads from
 * `strings.xml`, so the core package itself stays entirely text-free (§13.1 of the plan).
 */

@Composable
fun PublicFailure.title(): String = stringResource(
    when (this) {
        PublicFailure.OFFLINE -> R.string.retry_failure_offline_title
        PublicFailure.CONNECTION -> R.string.retry_failure_connection_title
        PublicFailure.TIMEOUT -> R.string.retry_failure_timeout_title
        PublicFailure.VALIDATION -> R.string.retry_failure_validation_title
        PublicFailure.NOT_FOUND -> R.string.retry_failure_not_found_title
        PublicFailure.CONFLICT -> R.string.retry_failure_conflict_title
        PublicFailure.PERMISSION -> R.string.retry_failure_permission_title
        PublicFailure.RATE_LIMITED -> R.string.retry_failure_rate_limited_title
        PublicFailure.SERVER -> R.string.retry_failure_server_title
        PublicFailure.PROTOCOL -> R.string.retry_failure_protocol_title
        PublicFailure.AMBIGUOUS_MUTATION -> R.string.retry_failure_ambiguous_mutation_title
        PublicFailure.UNKNOWN -> R.string.retry_failure_unknown_title
    },
)

@Composable
fun PublicFailure.description(): String = stringResource(
    when (this) {
        PublicFailure.OFFLINE -> R.string.retry_failure_offline_description
        PublicFailure.CONNECTION -> R.string.retry_failure_connection_description
        PublicFailure.TIMEOUT -> R.string.retry_failure_timeout_description
        PublicFailure.VALIDATION -> R.string.retry_failure_validation_description
        PublicFailure.NOT_FOUND -> R.string.retry_failure_not_found_description
        PublicFailure.CONFLICT -> R.string.retry_failure_conflict_description
        PublicFailure.PERMISSION -> R.string.retry_failure_permission_description
        PublicFailure.RATE_LIMITED -> R.string.retry_failure_rate_limited_description
        PublicFailure.SERVER -> R.string.retry_failure_server_description
        PublicFailure.PROTOCOL -> R.string.retry_failure_protocol_description
        PublicFailure.AMBIGUOUS_MUTATION -> R.string.retry_failure_ambiguous_mutation_description
        PublicFailure.UNKNOWN -> R.string.retry_failure_unknown_description
    },
)

@Composable
fun RecoveryAction.label(): String = stringResource(
    when (this) {
        RecoveryAction.Retry -> R.string.recovery_retry
        RecoveryAction.EditInput -> R.string.recovery_edit_input
        RecoveryAction.Authenticate -> R.string.recovery_authenticate
        RecoveryAction.GoBack -> R.string.recovery_go_back
        RecoveryAction.ContactSupport -> R.string.recovery_contact_support
    },
)
