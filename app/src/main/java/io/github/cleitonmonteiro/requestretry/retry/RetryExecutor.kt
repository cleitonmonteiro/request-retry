package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

class RetryExecutor(
    private val failureClassifier: FailureClassifier = DefaultFailureClassifier,
    private val retryDecider: RetryDecider = ConservativeRetryDecider,
    private val retryBudget: RetryBudget = UnlimitedRetryBudget,
    private val circuitBreaker: CircuitBreaker = NoOpCircuitBreaker,
    private val bulkhead: Bulkhead = Bulkhead(),
    private val observer: RetryObserver = NoOpRetryObserver,
    private val clock: MonotonicClock = SystemMonotonicClock,
) {
    internal suspend fun <I, O> execute(
        input: I,
        spec: OperationSpec,
        call: OneShotCall<I, O>,
        onProgress: suspend (ExecutionProgress) -> Unit,
    ): ExecutionOutcome<O> {
        val startedNanos = clock.nowNanos()
        retryBudget.onOriginalRequest(spec.name)
        observer.onOperationStarted(OperationTelemetryContext(spec.name, spec.maxAttempts))
        var attempt = 1

        while (true) {
            val remaining = remaining(spec, startedNanos)
            if (!remaining.isPositive()) return deadlineExceeded(spec, attempt - 1)
            if (!circuitBreaker.allow(spec.name)) {
                return finishFailure(spec, PublicFailure.CircuitOpen, RecoveryAction.Retry, attempt - 1)
            }

            onProgress(ExecutionProgress.AttemptStarted(attempt))
            observer.onAttemptStarted(AttemptTelemetryContext(spec.name, attempt))
            val context = AttemptContext(spec.operationId, spec.name, attempt, spec.maxAttempts)

            val result = try {
                val timeout = minOf(spec.perAttemptTimeout, remaining)
                val value = withTimeout(timeout.inWholeMilliseconds.coerceAtLeast(1L)) {
                    bulkhead.execute { call.execute(input, context) }
                }
                circuitBreaker.onSuccess(spec.name)
                observer.onAttemptFinished(AttemptTelemetryResult(spec.name, attempt, true, null))
                val outcome = ExecutionOutcome.Success(value, attempt)
                observer.onOperationFinished(OperationTelemetryResult(spec.name, "succeeded", attempt))
                return outcome
            } catch (error: TimeoutCancellationException) {
                RequestFailure.Timeout(
                    stage = TimeoutStage.RESPONSE_HEADERS,
                    outcomeCertainty = if (spec.safety is OperationSafety.ReadOnly) {
                        OutcomeCertainty.NOT_SENT
                    } else {
                        OutcomeCertainty.MAY_HAVE_REACHED_SERVER
                    },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Error) {
                throw error
            } catch (error: Throwable) {
                classifySafely(error)
            }

            circuitBreaker.onFailure(spec.name, result)
            observer.onAttemptFinished(
                AttemptTelemetryResult(spec.name, attempt, false, result.telemetryCategory()),
            )
            when (val decision = decideSafely(spec, result, attempt)) {
                RetryDecision.VerifyStatus -> {
                    observer.onOperationFinished(OperationTelemetryResult(spec.name, "outcome_unknown", attempt))
                    return ExecutionOutcome.Unknown(spec.operationId)
                }
                is RetryDecision.Stop -> return finishFailure(
                    spec = spec,
                    failure = decision.failure,
                    recovery = decision.recovery,
                    attemptsUsed = attempt,
                )
                is RetryDecision.Retry -> {
                    if (!retryBudget.tryAcquireRetry(spec.name)) {
                        return finishFailure(
                            spec,
                            PublicFailure.RetryBudgetExhausted,
                            RecoveryAction.Retry,
                            attempt,
                        )
                    }
                    val remainingBeforeDelay = remaining(spec, startedNanos)
                    val localDelay = try {
                        spec.backoff.delayForRetry(attempt - 1)
                    } catch (error: Error) {
                        throw error
                    } catch (_: Throwable) {
                        return finishFailure(spec, PublicFailure.Local, RecoveryAction.ContactSupport, attempt)
                    }
                    val selected = maxOf(localDelay, decision.serverDelay ?: Duration.ZERO)
                    val delayDuration = minOf(selected, spec.backoff.maxDelay, remainingBeforeDelay)
                    if (!remainingBeforeDelay.isPositive() || delayDuration >= remainingBeforeDelay) {
                        return deadlineExceeded(spec, attempt)
                    }
                    observer.onRetryScheduled(
                        RetryScheduledEvent(spec.name, attempt + 1, delayDuration, decision.reason),
                    )
                    onProgress(ExecutionProgress.RetryScheduled(attempt + 1, delayDuration, decision.reason))
                    if (delayDuration.isPositive()) delay(delayDuration)
                    attempt++
                }
            }
        }
    }

    private fun remaining(spec: OperationSpec, startedNanos: Long): Duration {
        val elapsed = (clock.nowNanos() - startedNanos).coerceAtLeast(0L).nanoseconds
        return spec.overallDeadline - elapsed
    }

    private fun classifySafely(error: Throwable): RequestFailure = try {
        failureClassifier.classify(error)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (fatal: Error) {
        throw fatal
    } catch (_: Throwable) {
        RequestFailure.Local("failure_classifier")
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

    private fun deadlineExceeded(spec: OperationSpec, attemptsUsed: Int): ExecutionOutcome.Failure =
        finishFailure(spec, PublicFailure.DeadlineExceeded, RecoveryAction.Retry, attemptsUsed)

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
