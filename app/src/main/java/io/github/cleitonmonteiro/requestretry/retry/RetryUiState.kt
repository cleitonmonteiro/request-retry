package io.github.cleitonmonteiro.requestretry.retry

/** The three states any retry-backed screen can be in. */
sealed interface RetryUiState<out T> {

    /**
     * [backoffSecondsRemaining] is null while the call itself is in flight (counting down
     * otherwise). [retryAttempt]/[maxRetries] are both null for the very first attempt (via
     * [RetryController.load]); an attempt triggered by [RetryController.retry] instead carries
     * the same numbers [Feedback.retriesUsed]/[Feedback.maxRetries] would report if it failed —
     * letting the UI style a retry differently from the initial load.
     */
    data class Loading(
        val backoffSecondsRemaining: Int? = null,
        val retryAttempt: Int? = null,
        val maxRetries: Int? = null,
    ) : RetryUiState<Nothing>

    data class Success<T>(val data: T) : RetryUiState<T>

    data class Feedback(
        val message: String,
        val retriesUsed: Int,
        val maxRetries: Int,
        val canRetry: Boolean,
    ) : RetryUiState<Nothing>
}
