package io.github.cleitonmonteiro.requestretry.data.observability

import android.util.Log
import io.github.cleitonmonteiro.requestretry.BuildConfig
import io.github.cleitonmonteiro.requestretry.retry.AttemptTelemetryContext
import io.github.cleitonmonteiro.requestretry.retry.AttemptTelemetryResult
import io.github.cleitonmonteiro.requestretry.retry.OperationTelemetryContext
import io.github.cleitonmonteiro.requestretry.retry.OperationTelemetryResult
import io.github.cleitonmonteiro.requestretry.retry.RetryObserver
import io.github.cleitonmonteiro.requestretry.retry.RetryScheduledEvent
import javax.inject.Inject

/** Debug-only, payload-free observer. Production can replace this binding with metrics/tracing. */
class SanitizedRetryObserver @Inject constructor() : RetryObserver {
    override fun onOperationStarted(context: OperationTelemetryContext) = log(
        "operation_started name=${context.operationName} max_attempts=${context.maxAttempts}",
    )

    override fun onAttemptStarted(context: AttemptTelemetryContext) = log(
        "attempt_started name=${context.operationName} attempt=${context.attempt}",
    )

    override fun onAttemptFinished(result: AttemptTelemetryResult) = log(
        "attempt_finished name=${result.operationName} attempt=${result.attempt} " +
            "success=${result.succeeded} category=${result.failureCategory}",
    )

    override fun onRetryScheduled(event: RetryScheduledEvent) = log(
        "retry_scheduled name=${event.operationName} next=${event.nextAttempt} " +
            "delay_ms=${event.delay.inWholeMilliseconds} reason=${event.reason::class.simpleName}",
    )

    override fun onOperationFinished(result: OperationTelemetryResult) = log(
        "operation_finished name=${result.operationName} outcome=${result.outcome} " +
            "attempts=${result.attemptsUsed}",
    )

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d("RetryTelemetry", message)
    }
}
