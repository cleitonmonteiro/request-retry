package io.github.cleitonmonteiro.requestretry.retry

/** The lifecycle states any retry-backed screen can be in. */
sealed interface RetryUiState<out T> {

    /** No request has been started yet. */
    data object Idle : RetryUiState<Nothing>

    /** A call is in flight. [attempt] is 1-based; `1` is the initial load, not a retry. */
    data class Loading(
        val attempt: Int = 1,
        val maxAttempts: Int = OperationSpec.DEFAULT_MAX_ATTEMPTS,
    ) : RetryUiState<Nothing>

    /**
     * Counting down to a retry the user asked for — the state that didn't exist before: the
     * backoff delay used to run silently inside [Loading]. [nextAttempt] is the attempt about to
     * start once the countdown reaches zero.
     */
    data class BackingOff(
        val nextAttempt: Int,
        val maxAttempts: Int,
        val secondsRemaining: Int,
        val reason: RetryReason,
    ) : RetryUiState<Nothing>

    data class Success<T>(val data: T, val attemptsUsed: Int = 1) : RetryUiState<T>

    /**
     * A failed, classified attempt. [recovery] — not a raw boolean — is what tells the UI
     * whether a Retry button belongs here at all, or something else does (edit the form,
     * authenticate, go back, contact support). [serverMessage], when present, is the server's
     * own text for the failure (currently only populated for a `422`) and takes priority over
     * [failure]'s generic local copy — see `ui/components/RetryPresentation.kt`.
     */
    data class Feedback(
        val failure: PublicFailure,
        val recovery: RecoveryAction,
        val attemptsUsed: Int,
        val maxAttempts: Int,
        val serverMessage: String? = null,
    ) : RetryUiState<Nothing> {
        val canRetry: Boolean get() = recovery == RecoveryAction.Retry

        /** Derived, never stored: `3` attempts is `2` retries — see the plan's §9.3 vocabulary fix. */
        val retriesUsed: Int get() = attemptsUsed - 1
    }
}
