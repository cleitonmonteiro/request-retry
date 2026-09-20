package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import kotlin.time.Duration

interface RetryObserver {
    fun onOperationStarted(context: OperationTelemetryContext) = Unit
    fun onAttemptStarted(context: AttemptTelemetryContext) = Unit
    fun onAttemptFinished(result: AttemptTelemetryResult) = Unit
    fun onRetryScheduled(event: RetryScheduledEvent) = Unit
    fun onOperationFinished(result: OperationTelemetryResult) = Unit
}

data class OperationTelemetryContext(val operationName: OperationName, val maxAttempts: Int)
data class AttemptTelemetryContext(val operationName: OperationName, val attempt: Int)
data class AttemptTelemetryResult(
    val operationName: OperationName,
    val attempt: Int,
    val succeeded: Boolean,
    val failureCategory: String?,
)
data class RetryScheduledEvent(
    val operationName: OperationName,
    val nextAttempt: Int,
    val delay: Duration,
    val reason: RetryReason,
)
data class OperationTelemetryResult(
    val operationName: OperationName,
    val outcome: String,
    val attemptsUsed: Int,
)

object NoOpRetryObserver : RetryObserver

internal fun RequestFailure.telemetryCategory(): String = when (this) {
    RequestFailure.Offline -> "offline"
    is RequestFailure.Dns -> "dns"
    is RequestFailure.Tls -> "tls"
    is RequestFailure.Connection -> "connection"
    is RequestFailure.Timeout -> "timeout"
    is RequestFailure.Http -> "http_${statusCode / 100}xx"
    RequestFailure.AuthenticationRequired -> "authentication"
    RequestFailure.PermissionDenied -> "permission"
    is RequestFailure.Validation -> "validation"
    is RequestFailure.Conflict -> "conflict"
    is RequestFailure.RateLimited -> "rate_limited"
    is RequestFailure.Protocol -> "protocol"
    is RequestFailure.Local -> "local"
    is RequestFailure.Unknown -> "unknown"
}
