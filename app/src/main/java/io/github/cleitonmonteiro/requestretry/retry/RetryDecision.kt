package io.github.cleitonmonteiro.requestretry.retry

/** Why a delay is being applied before the next attempt — carried through to [RetryObserver] and [RetryUiState.BackingOff]. */
enum class RetryReason {
    /** The first attempt of a session — no delay involved. */
    MANUAL,

    /** Computed by the injected [RetryPolicy]. */
    BACKOFF,

    /** Taken from the failure's `Retry-After`, capped locally. */
    RETRY_AFTER_HEADER,
}

/** What [RetryDecider] concluded about one failed attempt. */
data class RetryDecision(
    val publicFailure: PublicFailure,
    val recovery: RecoveryAction,
    /** The server's own error text, when the failure carried one — see [io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure.Http.message]. */
    val serverMessage: String? = null,
)
