package io.github.cleitonmonteiro.requestretry.retry

/** The three states any retry-backed screen can be in. */
sealed interface RetryUiState<out T> {

    /** [backoffSecondsRemaining] is null while the call itself is in flight. */
    data class Loading(val backoffSecondsRemaining: Int? = null) : RetryUiState<Nothing>

    data class Success<T>(val data: T) : RetryUiState<T>

    data class Feedback(
        val message: String,
        val retriesUsed: Int,
        val maxRetries: Int,
        val canRetry: Boolean,
    ) : RetryUiState<Nothing>
}
