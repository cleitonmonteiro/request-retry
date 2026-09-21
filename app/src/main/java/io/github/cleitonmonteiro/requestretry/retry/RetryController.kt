package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Drives a mocked [apiCall] through the load/retry/backoff lifecycle shared by every
 * retry-backed screen in this app, according to [spec]. A screen's ViewModel owns one of these
 * and exposes [state] as-is; the payload type [T] is the only thing that varies between screens.
 *
 * Every request into this class — [load] and [retry] — runs its decide-and-publish step
 * *synchronously* on the caller's thread, guarded by [commandLock]'s non-suspending [Mutex.tryLock]:
 * that's what closes the double-tap race across dispatchers the plan's §11.1 calls out, while
 * still publishing the new [RetryUiState] before the caller regains control, exactly like the
 * original implementation did — see `RetryControllerTest`'s double-tap test. Only the actual
 * delay + network call runs in a launched coroutine.
 */
class RetryController<T>(
    private val scope: CoroutineScope,
    private val apiCall: ApiCall<T>,
    private val spec: OperationSpec = OperationSpec.read(OperationName.DEMO),
    private val retryPolicy: RetryPolicy = ExponentialBackoffPolicy(),
    private val decider: RetryDecider = DefaultRetryDecider,
    private val executor: RetryExecutor = RetryExecutor(),
    private val observer: RetryObserver = RetryObserver.NoOp,
) {
    private val _state = MutableStateFlow<RetryUiState<T>>(RetryUiState.Idle)
    val state: StateFlow<RetryUiState<T>> = _state.asStateFlow()

    private val commandLock = Mutex()
    private var job: Job? = null
    private var generation = 0L

    /** The `Retry-After` from the last failure, if any — read back by [retry] to time the delay. */
    private var lastRetryAfter: Duration? = null

    /** Starts (or restarts) the call from a clean slate, resetting the attempt budget. */
    fun load() {
        if (!commandLock.tryLock()) return
        try {
            when (spec.concurrency) {
                ConcurrencyPolicy.CancelPrevious -> job?.cancel()
                ConcurrencyPolicy.DropWhileRunning -> if (job?.isActive == true) {
                    observer.onDropped(spec.name)
                    return
                }
            }
            lastRetryAfter = null
            publishAndLaunch(attempt = 1, delayBefore = Duration.ZERO, reason = RetryReason.MANUAL)
        } finally {
            commandLock.unlock()
        }
    }

    /** Retries after a backoff delay. No-op unless the current [RetryUiState.Feedback] allows it. */
    fun retry() {
        if (!commandLock.tryLock()) return
        try {
            val current = _state.value
            if (current !is RetryUiState.Feedback || !current.canRetry) return
            if (spec.concurrency == ConcurrencyPolicy.DropWhileRunning && job?.isActive == true) return

            val retryAfter = lastRetryAfter
            val (delayBefore, reason) = if (retryAfter != null) {
                retryAfter.coerceAtMost(RETRY_AFTER_CAP) to RetryReason.RETRY_AFTER_HEADER
            } else {
                retryPolicy.delayFor(current.attemptsUsed) to RetryReason.BACKOFF
            }
            publishAndLaunch(attempt = current.attemptsUsed + 1, delayBefore = delayBefore, reason = reason)
        } finally {
            commandLock.unlock()
        }
    }

    private fun publishAndLaunch(attempt: Int, delayBefore: Duration, reason: RetryReason) {
        val runGeneration = ++generation
        _state.value = if (delayBefore > Duration.ZERO) {
            RetryUiState.BackingOff(attempt, spec.maxAttempts, secondsRemaining(delayBefore), reason)
        } else {
            RetryUiState.Loading(attempt, spec.maxAttempts)
        }
        job = scope.launch { runAttempt(attempt, delayBefore, reason, runGeneration) }
    }

    private suspend fun runAttempt(attempt: Int, delayBefore: Duration, reason: RetryReason, runGeneration: Long) {
        var remaining = delayBefore
        while (remaining > Duration.ZERO) {
            val tick = remaining.coerceAtMost(1.seconds)
            delay(tick)
            if (runGeneration != generation) return
            remaining -= tick
            if (remaining > Duration.ZERO) {
                _state.value = RetryUiState.BackingOff(attempt, spec.maxAttempts, secondsRemaining(remaining), reason)
            }
        }

        _state.value = RetryUiState.Loading(attempt, spec.maxAttempts)
        observer.onAttemptStarted(spec.name, attempt)
        val result = executor.attempt(apiCall, spec.perAttemptTimeout)
        if (runGeneration != generation) return

        when (result) {
            is AttemptResult.Success -> {
                lastRetryAfter = null
                observer.onFinished(spec.name, OperationResult.SUCCEEDED)
                _state.value = RetryUiState.Success(result.value, attempt)
            }
            is AttemptResult.Failure -> {
                observer.onAttemptFailed(spec.name, attempt, result.failure)
                publishTerminal(attempt, result.failure)
            }
        }
    }

    private fun publishTerminal(attempt: Int, failure: RequestFailure) {
        val decision = decider.decide(failure, spec, attempt)
        lastRetryAfter = (failure as? RequestFailure.Http)?.retryAfter
        observer.onFinished(
            spec.name,
            if (decision.recovery == RecoveryAction.Retry) OperationResult.RETRYABLE else OperationResult.TERMINAL,
        )
        _state.value = RetryUiState.Feedback(
            failure = decision.publicFailure,
            recovery = decision.recovery,
            attemptsUsed = attempt,
            maxAttempts = spec.maxAttempts,
            serverMessage = decision.serverMessage,
        )
    }

    private fun secondsRemaining(remaining: Duration): Int =
        remaining.inWholeMilliseconds.coerceAtLeast(1).let { ms -> ((ms + 999) / 1000).toInt() }

    private companion object {
        /** Never wait longer than this on a server-supplied `Retry-After`, however large it asks. */
        val RETRY_AFTER_CAP = 30.seconds
    }
}
