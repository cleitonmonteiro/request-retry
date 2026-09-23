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

/** Payload-free metadata emitted each time the executor starts, once per manual attempt. */
data class OperationTelemetryContext(val operationName: OperationName, val maxAttempts: Int)

/** Payload-free metadata emitted when a single HTTP attempt starts. */
data class AttemptTelemetryContext(val operationName: OperationName, val attempt: Int)

/** Sanitized outcome metadata for one HTTP attempt; [failureCategory] is null on success. */
data class AttemptTelemetryResult(
    val operationName: OperationName,
    val attempt: Int,
    val succeeded: Boolean,
    val failureCategory: String?,
)

/**
 * Cooldown metadata computed when a retryable failure decides its next-attempt cooldown. The
 * cooldown itself is spent at the start of that next manual attempt, not before this event
 * returns control to the user.
 */
data class RetryScheduledEvent(
    val operationName: OperationName,
    val nextAttempt: Int,
    val delay: Duration,
)

/**
 * Metadata emitted right before the executor suspends for a previously scheduled cooldown, at the
 * start of the next manual attempt — not at the tail of the attempt that failed.
 */
data class BackoffDelayContext(val operationName: OperationName, val delay: Duration)

/**
 * Sanitized metadata emitted when one execution ends; [outcome] is `succeeded`,
 * `manual_retry_available`, or `failed`.
 */
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
