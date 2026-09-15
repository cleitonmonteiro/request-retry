package io.github.cleitonmonteiro.requestretry.retry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
        run(delayBefore = Duration.ZERO)
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
        job = scope.launch {
            if (delayBefore > Duration.ZERO) awaitBackoff(delayBefore)
            _state.value = RetryUiState.Loading(backoffSecondsRemaining = null)
            runCatching { apiCall() }
                .onSuccess { _state.value = RetryUiState.Success(it) }
                .onFailure { error ->
                    _state.value = RetryUiState.Feedback(
                        message = error.message ?: "Something went wrong",
                        retriesUsed = retriesUsed,
                        maxRetries = maxRetries,
                        canRetry = retriesUsed < maxRetries,
                    )
                }
        }
    }

    /** Counts down whole seconds first, then sleeps out whatever fractional part is left. */
    private suspend fun awaitBackoff(totalDelay: Duration) {
        val wholeSeconds = totalDelay.inWholeSeconds.toInt()
        for (secondsLeft in wholeSeconds downTo 1) {
            _state.value = RetryUiState.Loading(backoffSecondsRemaining = secondsLeft)
            delay(1.seconds)
        }
        val fractional = totalDelay - wholeSeconds.seconds
        if (fractional > Duration.ZERO) delay(fractional)
    }
}
