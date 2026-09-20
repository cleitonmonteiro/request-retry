package io.github.cleitonmonteiro.requestretry.retry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Drives a mocked [apiCall] through the load/retry/backoff lifecycle shared by every
 * retry-backed screen in this app. A screen's ViewModel owns one of these and exposes
 * [state] as-is; the payload type [T] is the only thing that varies between screens.
 */
class RetryController<T>(
    private val scope: CoroutineScope,
    private val apiCall: ApiCall<T>,
    private val retryPolicy: RetryPolicy = ExponentialBackoffPolicy(),
    private val maxAttempts: Int = RetryControllerFactory.DEFAULT_MAX_ATTEMPTS,
    private val errorTypeStrategy: ErrorTypeStrategy = DefaultErrorTypeStrategy,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
    }

    private val _state = MutableStateFlow<RetryUiState<T>>(RetryUiState.Idle)
    val state: StateFlow<RetryUiState<T>> = _state.asStateFlow()

    private var job: Job? = null
    private var generation = 0L

    /** Starts (or restarts) the call from a clean slate, resetting the retry budget. */
    fun load() = run(attemptNumber = 1, delayBefore = Duration.ZERO)

    /** Retries the call after a backoff delay. No-op once the retry budget is spent. */
    fun retry() {
        val current = _state.value
        if (current !is RetryUiState.Feedback || !current.canRetry) return
        val nextAttempt = current.attemptsUsed + 1
        val delayBefore = retryPolicy.delayFor(current.attemptsUsed).coerceAtLeast(Duration.ZERO)
        run(attemptNumber = nextAttempt, delayBefore = delayBefore)
    }

    private fun run(attemptNumber: Int, delayBefore: Duration) {
        job?.cancel()
        val runGeneration = ++generation
        // Set synchronously, before the coroutine below is even scheduled: retry()'s in-flight
        // guard reads _state.value, so a second tap between this call and the coroutine's first
        // suspension point must already see Loading, not the stale Feedback it's superseding —
        // otherwise a fast double-tap (or a sub-second backoff, which never enters the countdown
        // loop below) can slip past the guard and spend two retries on one attempt.
        _state.value = loadingState(attemptNumber)
        job = scope.launch {
            if (delayBefore > Duration.ZERO) delay(delayBefore)
            flow { emit(apiCall().single()) }
                .map<T, RetryUiState<T>> { RetryUiState.Success(it) }
                .onStart { emit(loadingState(attemptNumber)) }
                .catch { error ->
                    // Flow.catch is transparent to cancellation and rethrows it rather than
                    // reaching this block, so a superseded job (a newer load()/retry()) dies
                    // quietly instead of being reported as a failed request.
                    if (error is CancellationException || error is Error) throw error
                    val type = errorTypeStrategy.classify(error)
                    val canRetry = type != ErrorType.UNPROCESSABLE_ENTITY && attemptNumber < maxAttempts
                    emit(
                        RetryUiState.Feedback(
                            error = type.feedback(canRetry),
                            attemptsUsed = attemptNumber,
                            maxAttempts = maxAttempts,
                            canRetry = canRetry,
                        )
                    )
                }
                .collect { if (runGeneration == generation) _state.value = it }
        }
    }

    /**
     * Builds a [RetryUiState.Loading] carrying the current retry attempt (null for the first
     * attempt via [load], non-null once [retry] has bumped [retriesUsed]) — [retriesUsed] is
     * stable for the whole duration of one [run] call, so the snapshot is threaded through rather
     * than reading mutable controller state from a superseded coroutine.
     */
    private fun loadingState(attemptNumber: Int): RetryUiState.Loading {
        val retryAttempt = attemptNumber.takeIf { it > 1 }
        return RetryUiState.Loading(
            retryAttempt = retryAttempt,
            maxRetries = retryAttempt?.let { maxAttempts },
        )
    }
}
