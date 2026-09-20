package io.github.cleitonmonteiro.requestretry.retry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
) {
    private val _state = MutableStateFlow<RetryUiState<T>>(RetryUiState.Loading())
    val state: StateFlow<RetryUiState<T>> = _state.asStateFlow()

    private var retriesUsed = 0
    private var job: Job? = null

    /** Starts (or restarts) the call from a clean slate, resetting the retry budget. */
    fun load() {
        retriesUsed = 0
//        run(delayBefore = Duration.ZERO)
        run(delayBefore = 5.seconds)
    }

    /** Retries the call after a backoff delay. No-op once the retry budget is spent. */
    fun retry() {
        val current = _state.value
        if (current !is RetryUiState.Feedback || !current.canRetry) return
        retriesUsed++
        run(delayBefore = retryPolicy.delayFor(retriesUsed))
    }

    private fun run(delayBefore: Duration) {
        job?.cancel()
        // Set synchronously, before the coroutine below is even scheduled: retry()'s in-flight
        // guard reads _state.value, so a second tap between this call and the coroutine's first
        // suspension point must already see Loading, not the stale Feedback it's superseding —
        // otherwise a fast double-tap (or a sub-second backoff, which never enters the countdown
        // loop below) can slip past the guard and spend two retries on one attempt.
        _state.value = loadingState(delayBefore.inWholeSeconds.toInt().takeIf { it > 0 })
        job = scope.launch {
            if (delayBefore > Duration.ZERO) awaitBackoff(delayBefore)
            apiCall()
                .map<T, RetryUiState<T>> { RetryUiState.Success(it) }
                .onStart { emit(loadingState(backoffSecondsRemaining = null)) }
                .catch { error ->
                    // Flow.catch is transparent to cancellation and rethrows it rather than
                    // reaching this block, so a superseded job (a newer load()/retry()) dies
                    // quietly instead of being reported as a failed request.
                    emit(
                        RetryUiState.Feedback(
                            // Only IOException (what ApiClient's scenario injection throws) has
                            // a message meant for a user; anything else — a mapper throwing on
                            // a malformed response, say — falls back to a generic message rather
                            // than leaking an internal exception string into the UI.
                            message = (error as? IOException)?.message ?: "Something went wrong",
                            retriesUsed = retriesUsed,
                            maxRetries = maxRetries,
                            canRetry = retriesUsed < maxRetries,
                        )
                    )
                }
                .collect { _state.value = it }
        }
    }

    /** Sleeps out any fractional remainder first, then counts down the whole seconds left,
     * so the last visible tick ends exactly when [totalDelay] has elapsed instead of lingering. */
    private suspend fun awaitBackoff(totalDelay: Duration) {
        val wholeSeconds = totalDelay.inWholeSeconds.toInt()
        val fractional = totalDelay - wholeSeconds.seconds
        if (fractional > Duration.ZERO) delay(fractional)
        for (secondsLeft in wholeSeconds downTo 1) {
            _state.value = loadingState(backoffSecondsRemaining = secondsLeft)
            delay(1.seconds)
        }
    }

    /**
     * Builds a [RetryUiState.Loading] carrying the current retry attempt (null for the first
     * attempt via [load], non-null once [retry] has bumped [retriesUsed]) — [retriesUsed] is
     * stable for the whole duration of one [run] call, so it's read directly rather than
     * threaded through as a parameter.
     */
    private fun loadingState(backoffSecondsRemaining: Int?): RetryUiState.Loading {
        val retryAttempt = retriesUsed.takeIf { it > 0 }
        return RetryUiState.Loading(
            backoffSecondsRemaining = backoffSecondsRemaining,
            retryAttempt = retryAttempt,
            maxRetries = retryAttempt?.let { maxRetries },
        )
    }
}
