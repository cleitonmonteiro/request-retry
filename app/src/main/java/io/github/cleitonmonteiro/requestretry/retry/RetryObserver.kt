package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import kotlin.time.Duration

/** Receives controlled, payload-free telemetry about operation execution. */
interface RetryObserver {
    fun onOperationStarted(context: OperationTelemetryContext) = Unit
    fun onAttemptStarted(context: AttemptTelemetryContext) = Unit
    fun onAttemptFinished(result: AttemptTelemetryResult) = Unit
    fun onRetryScheduled(event: RetryScheduledEvent) = Unit
    fun onBackoffDelayStarted(context: BackoffDelayContext) = Unit
    fun onOperationFinished(result: OperationTelemetryResult) = Unit
}

/** Payload-free metadata emitted when a logical operation starts. */
data class OperationTelemetryContext(val operationName: OperationName, val maxAttempts: Int)

/** Payload-free metadata emitted when a single HTTP attempt starts. */
data class AttemptTelemetryContext(val operationName: OperationName, val attempt: Int)

/** Sanitized outcome metadata for one HTTP attempt. */
data class AttemptTelemetryResult(
    val operationName: OperationName,
    val attempt: Int,
    val succeeded: Boolean,
    val failureCategory: String?,
)
/** Cooldown metadata emitted before control is returned for a manual retry. */
data class RetryScheduledEvent(
    val operationName: OperationName,
    val nextAttempt: Int,
    val delay: Duration,
    val reason: RetryReason,
)
/** Metadata emitted right before the executor suspends for the backoff cooldown. */
data class BackoffDelayContext(val operationName: OperationName, val delay: Duration)

/** Sanitized terminal outcome metadata for a logical operation. */
data class OperationTelemetryResult(
    val operationName: OperationName,
    val outcome: String,
    val attemptsUsed: Int,
)

/** Default observer that deliberately discards all telemetry callbacks. */
object NoOpRetryObserver : RetryObserver

internal fun RequestFailure.telemetryCategory(): String = when (this) {
    RequestFailure.Offline -> "offline"
    RequestFailure.Connection -> "connection"
    is RequestFailure.Http -> "http_${statusCode / 100}xx"
    RequestFailure.Generic -> "generic"
    RequestFailure.Local -> "local"
    RequestFailure.Unknown -> "unknown"
}
