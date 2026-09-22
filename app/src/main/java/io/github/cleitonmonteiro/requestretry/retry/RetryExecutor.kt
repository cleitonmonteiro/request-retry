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

/** Executes one HTTP call. A retryable failure waits through its cooldown, then returns control to the user. */
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
        onProgress: suspend (ExecutionProgress) -> Unit,
    ): ExecutionOutcome<O> {
        observer.onOperationStarted(OperationTelemetryContext(spec.name, spec.maxAttempts))
        onProgress(ExecutionProgress.AttemptStarted(attempt))
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
                val localDelay = runFailSafe(fallback = {
                    return finishFailure(spec, PublicFailure.Local, RecoveryAction.ContactSupport, attempt)
                }) { spec.backoff.delayForRetry(attempt - 1) }
                val selected = maxOf(localDelay, decision.serverDelay ?: Duration.ZERO)
                val delayDuration = minOf(selected, spec.backoff.maxDelay)
                observer.onRetryScheduled(
                    RetryScheduledEvent(spec.name, attempt + 1, delayDuration, decision.reason),
                )
                onProgress(ExecutionProgress.RetryScheduled(attempt + 1, delayDuration, decision.reason))
                if (delayDuration.isPositive()) {
                    observer.onBackoffDelayStarted(BackoffDelayContext(spec.name, delayDuration))
                    delay(delayDuration)
                }
                observer.onOperationFinished(OperationTelemetryResult(spec.name, "manual_retry_available", attempt))
                ExecutionOutcome.ManualRetry(decision.failure, attempt)
            }
        }
    }

    private fun classifySafely(error: Throwable): RequestFailure =
        runFailSafe(fallback = { RequestFailure.Local }) { failureClassifier.classify(error) }

    private fun decideSafely(
        spec: OperationSpec,
        failure: RequestFailure,
        attempt: Int,
    ): RetryDecision = runFailSafe(fallback = { RetryDecision.Stop(PublicFailure.Local, RecoveryAction.ContactSupport) }) {
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
