package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.time.Duration

/**
 * Runs [block], never swallowing a fatal [Error] or the cancellation of the calling coroutine; any
 * other throwable is mapped by [fallback]. That includes a [CancellationException] thrown while the
 * caller is still active, such as a `withTimeout` inside [block]: rethrowing it would end the
 * execution without an outcome.
 */
private suspend inline fun <T> runFailSafe(fallback: (Throwable) -> T, block: () -> T): T = try {
    block()
} catch (cancellation: CancellationException) {
    currentCoroutineContext().ensureActive()
    fallback(cancellation)
} catch (fatal: Error) {
    throw fatal
} catch (error: Throwable) {
    fallback(error)
}

/**
 * Runs exactly one call per logical attempt and never makes a second one on its own. A retryable
 * failure returns [ExecutionOutcome.ManualRetry] immediately, carrying the cooldown to spend at the
 * start of the next manual attempt the user triggers.
 */
class RetryExecutor(
    private val failureClassifier: FailureClassifier = DefaultFailureClassifier,
    private val retryDecider: RetryDecider = ConservativeRetryDecider,
    private val observer: RetryObserver = NoOpRetryObserver,
) {
    /**
     * Spends [pendingRetry] first when it is positive, then runs [call] once as [attempt].
     * Cancellation of this coroutine and fatal errors propagate; failures from the call,
     * classifier, decider, or backoff end in a fail-closed outcome, and observer failures are
     * ignored.
     */
    internal suspend fun <I, O> execute(
        input: I,
        spec: OperationSpec,
        attempt: Int,
        call: OneShotCall<I, O>,
        pendingRetry: Duration? = null,
    ): ExecutionOutcome<O> {
        notify { onOperationStarted(OperationTelemetryContext(spec.name, spec.maxAttempts)) }
        if (pendingRetry != null && pendingRetry.isPositive()) {
            notify { onBackoffDelayStarted(BackoffDelayContext(spec.name, pendingRetry)) }
            delay(pendingRetry)
        }
        notify { onAttemptStarted(AttemptTelemetryContext(spec.name, attempt)) }
        val context = AttemptContext(spec.name, attempt, spec.maxAttempts)

        val value = runFailSafe(fallback = { return finishAttemptFailure(spec, classifySafely(it), attempt) }) {
            call.execute(input, context)
        }

        notify { onAttemptFinished(AttemptTelemetryResult(spec.name, attempt, true, null)) }
        notify { onOperationFinished(OperationTelemetryResult(spec.name, "succeeded", attempt)) }
        return ExecutionOutcome.Success(value, attempt)
    }

    private suspend fun finishAttemptFailure(
        spec: OperationSpec,
        failure: RequestFailure,
        attempt: Int,
    ): ExecutionOutcome<Nothing> {
        notify { onAttemptFinished(AttemptTelemetryResult(spec.name, attempt, false, failure.telemetryCategory())) }
        return when (val decision = decideSafely(spec, failure, attempt)) {
            is RetryDecision.Stop -> finishFailure(spec, decision.failure, decision.recovery, attempt)
            is RetryDecision.Retry -> {
                val delayDuration = runFailSafe(fallback = {
                    return finishFailure(spec, PublicFailure.Local, RecoveryAction.Leave, attempt)
                }) { spec.backoff.delayForRetry(attempt - 1) }
                    .coerceIn(Duration.ZERO, spec.backoff.maxDelay)
                notify { onRetryScheduled(RetryScheduledEvent(spec.name, attempt + 1, delayDuration)) }
                notify { onOperationFinished(OperationTelemetryResult(spec.name, "manual_retry_available", attempt)) }
                ExecutionOutcome.ManualRetry(decision.failure, attempt, delayDuration)
            }
        }
    }

    private suspend fun classifySafely(error: Throwable): RequestFailure =
        runFailSafe(fallback = { RequestFailure.Local }) { failureClassifier.classify(error) }

    private suspend fun decideSafely(
        spec: OperationSpec,
        failure: RequestFailure,
        attempt: Int,
    ): RetryDecision = runFailSafe(fallback = { RetryDecision.Stop(PublicFailure.Local, RecoveryAction.Leave) }) {
        retryDecider.decide(RetryContext(spec, failure, attempt))
    }

    private suspend fun finishFailure(
        spec: OperationSpec,
        failure: PublicFailure,
        recovery: RecoveryAction,
        attemptsUsed: Int,
    ): ExecutionOutcome.Failure {
        notify { onOperationFinished(OperationTelemetryResult(spec.name, "failed", attemptsUsed)) }
        return ExecutionOutcome.Failure(failure, recovery, attemptsUsed)
    }

    /** Telemetry is best-effort: a throwing [observer] must never change or break an outcome. */
    private suspend inline fun notify(event: RetryObserver.() -> Unit) =
        runFailSafe(fallback = { }) { observer.event() }
}
