package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration

/** Runs [block], never swallowing cancellation or a fatal [Error]; any other throwable is mapped by [fallback]. */
private inline fun <T> runFailSafe(fallback: (Throwable) -> T, block: () -> T): T = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (fatal: Error) {
    throw fatal
} catch (error: Throwable) {
    fallback(error)
}

/**
 * Executes one HTTP call for one logical attempt. If [PendingRetry] carries a positive cooldown
 * left over from the previous failure, the executor suspends for it before this attempt's call
 * fires. A retryable failure returns control to the user immediately, carrying the cooldown to
 * spend at the start of the next manual attempt.
 */
class RetryExecutor(
    private val failureClassifier: FailureClassifier = DefaultFailureClassifier,
    private val retryDecider: RetryDecider = ConservativeRetryDecider,
    private val observer: RetryObserver = NoOpRetryObserver,
) {
    internal suspend fun <I, O> execute(
        input: I,
        spec: OperationSpec,
        attempt: Int,
        call: OneShotCall<I, O>,
        pendingRetry: PendingRetry? = null,
        onAttemptStarted: suspend (Int) -> Unit,
    ): ExecutionOutcome<O> {
        observer.onOperationStarted(OperationTelemetryContext(spec.name, spec.maxAttempts))
        if (pendingRetry != null && pendingRetry.delay.isPositive()) {
            observer.onBackoffDelayStarted(BackoffDelayContext(spec.name, pendingRetry.delay))
            delay(pendingRetry.delay)
        }
        onAttemptStarted(attempt)
        observer.onAttemptStarted(AttemptTelemetryContext(spec.name, attempt))
        val context = AttemptContext(spec.name, attempt, spec.maxAttempts)

        val result = runFailSafe(fallback = { classifySafely(it) }) {
            val value = call.execute(input, context)
            observer.onAttemptFinished(AttemptTelemetryResult(spec.name, attempt, true, null))
            observer.onOperationFinished(OperationTelemetryResult(spec.name, "succeeded", attempt))
            return ExecutionOutcome.Success(value, attempt)
        }

        observer.onAttemptFinished(
            AttemptTelemetryResult(spec.name, attempt, false, result.telemetryCategory()),
        )
        return when (val decision = decideSafely(spec, result, attempt)) {
            is RetryDecision.Stop -> finishFailure(spec, decision.failure, decision.recovery, attempt)
            is RetryDecision.Retry -> {
                val delayDuration = runFailSafe(fallback = {
                    return finishFailure(spec, PublicFailure.Local, RecoveryAction.Leave, attempt)
                }) { spec.backoff.delayForRetry(attempt - 1) }
                    .coerceIn(Duration.ZERO, spec.backoff.maxDelay)
                observer.onRetryScheduled(
                    RetryScheduledEvent(spec.name, attempt + 1, delayDuration),
                )
                observer.onOperationFinished(OperationTelemetryResult(spec.name, "manual_retry_available", attempt))
                ExecutionOutcome.ManualRetry(decision.failure, attempt, PendingRetry(delayDuration))
            }
        }
    }

    private fun classifySafely(error: Throwable): RequestFailure =
        runFailSafe(fallback = { RequestFailure.Local }) { failureClassifier.classify(error) }

    private fun decideSafely(
        spec: OperationSpec,
        failure: RequestFailure,
        attempt: Int,
    ): RetryDecision = runFailSafe(fallback = { RetryDecision.Stop(PublicFailure.Local, RecoveryAction.Leave) }) {
        retryDecider.decide(RetryContext(spec, failure, attempt))
    }

    private fun finishFailure(
        spec: OperationSpec,
        failure: PublicFailure,
        recovery: RecoveryAction,
        attemptsUsed: Int,
    ): ExecutionOutcome.Failure {
        observer.onOperationFinished(OperationTelemetryResult(spec.name, "failed", attemptsUsed))
        return ExecutionOutcome.Failure(failure, recovery, attemptsUsed)
    }
}
