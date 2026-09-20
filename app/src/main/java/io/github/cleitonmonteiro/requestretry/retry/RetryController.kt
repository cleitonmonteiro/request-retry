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
import java.io.IOException
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
    private val maxRetries: Int = 3,
    private val isRetryable: (Throwable) -> Boolean = { it is Exception },
) {
    init {
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
    }

    private val _state = MutableStateFlow<RetryUiState<T>>(RetryUiState.Idle)
    val state: StateFlow<RetryUiState<T>> = _state.asStateFlow()

    private var retriesUsed = 0
    private var job: Job? = null
    private var generation = 0L

    /** Starts (or restarts) the call from a clean slate, resetting the retry budget. */
    fun load() {
        retriesUsed = 0
        run(delayBefore = Duration.ZERO)
    }

    /** Retries the call after a backoff delay. No-op once the retry budget is spent. */
    fun retry() {
        val current = _state.value
        if (current !is RetryUiState.Feedback || !current.canRetry) return
        val nextAttempt = retriesUsed + 1
        val delayBefore = retryPolicy.delayFor(nextAttempt).coerceAtLeast(Duration.ZERO)
        retriesUsed = nextAttempt
        run(delayBefore)
    }

    private fun run(delayBefore: Duration) {
        job?.cancel()
        val runGeneration = ++generation
        val runRetriesUsed = retriesUsed
        // Set synchronously, before the coroutine below is even scheduled: retry()'s in-flight
        // guard reads _state.value, so a second tap between this call and the coroutine's first
        // suspension point must already see Loading, not the stale Feedback it's superseding —
        // otherwise a fast double-tap (or a sub-second backoff, which never enters the countdown
        // loop below) can slip past the guard and spend two retries on one attempt.
        _state.value = loadingState(runRetriesUsed)
        job = scope.launch {
            if (delayBefore > Duration.ZERO) delay(delayBefore)
            flow { emit(apiCall().single()) }
                .map<T, RetryUiState<T>> { RetryUiState.Success(it) }
                .onStart { emit(loadingState(runRetriesUsed)) }
                .catch { error ->
                    // Flow.catch is transparent to cancellation and rethrows it rather than
                    // reaching this block, so a superseded job (a newer load()/retry()) dies
                    // quietly instead of being reported as a failed request.
                    if (error is CancellationException || error is Error) throw error
                    emit(
                        RetryUiState.Feedback(
                            // Only IOException (what ApiClient's scenario injection throws) has
                            // a message meant for a user; anything else — a mapper throwing on
                            // a malformed response, say — falls back to a generic message rather
                            // than leaking an internal exception string into the UI.
                            message = (error as? IOException)?.message ?: "Something went wrong",
                            retriesUsed = runRetriesUsed,
                            maxRetries = maxRetries,
                            canRetry = isRetryable(error) && runRetriesUsed < maxRetries,
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
    private fun loadingState(retriesUsed: Int): RetryUiState.Loading {
        val retryAttempt = retriesUsed.takeIf { it > 0 }
        return RetryUiState.Loading(
            retryAttempt = retryAttempt,
            maxRetries = retryAttempt?.let { maxRetries },
        )
    }
}
