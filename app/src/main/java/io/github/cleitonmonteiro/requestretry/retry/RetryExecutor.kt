package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration

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
        val context = AttemptContext(spec.operationId, spec.name, attempt, spec.maxAttempts)

        val result = try {
            val value = call.execute(input, context)
            observer.onAttemptFinished(AttemptTelemetryResult(spec.name, attempt, true, null))
            observer.onOperationFinished(OperationTelemetryResult(spec.name, "succeeded", attempt))
            return ExecutionOutcome.Success(value, attempt)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Error) {
            throw error
        } catch (error: Throwable) {
            classifySafely(error)
        }

        observer.onAttemptFinished(
            AttemptTelemetryResult(spec.name, attempt, false, result.telemetryCategory()),
        )
        return when (val decision = decideSafely(spec, result, attempt)) {
            is RetryDecision.Stop -> finishFailure(spec, decision.failure, decision.recovery, attempt)
            is RetryDecision.Retry -> {
                val localDelay = try {
                    spec.backoff.delayForRetry(attempt - 1)
                } catch (error: Error) {
                    throw error
                } catch (_: Throwable) {
                    return finishFailure(spec, PublicFailure.Local, RecoveryAction.ContactSupport, attempt)
                }
                val selected = maxOf(localDelay, decision.serverDelay ?: Duration.ZERO)
                val delayDuration = minOf(selected, spec.backoff.maxDelay)
                observer.onRetryScheduled(
                    RetryScheduledEvent(spec.name, attempt + 1, delayDuration, decision.reason),
                )
                onProgress(ExecutionProgress.RetryScheduled(attempt + 1, delayDuration, decision.reason))
                if (delayDuration.isPositive()) delay(delayDuration)
                observer.onOperationFinished(OperationTelemetryResult(spec.name, "manual_retry_available", attempt))
                ExecutionOutcome.ManualRetry(decision.failure, attempt)
            }
        }
    }

    private fun classifySafely(error: Throwable): RequestFailure = try {
        failureClassifier.classify(error)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (fatal: Error) {
        throw fatal
    } catch (_: Throwable) {
        RequestFailure.Local
    }

    private fun decideSafely(
        spec: OperationSpec,
        failure: RequestFailure,
        attempt: Int,
    ): RetryDecision = try {
        retryDecider.decide(RetryContext(spec, failure, attempt))
    } catch (fatal: Error) {
        throw fatal
    } catch (_: Throwable) {
        RetryDecision.Stop(PublicFailure.Local, RecoveryAction.ContactSupport)
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
