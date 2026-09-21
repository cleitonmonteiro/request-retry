package io.github.cleitonmonteiro.requestretry.retry

/**
 * What the user can actually do about a [RetryUiState.Feedback] — replaces the old
 * `canRetry: Boolean`, which couldn't express "edit the form" or "go back" versus "retry".
 * The button (and its label) is a direct consequence of this value; the UI never inspects the
 * underlying [io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure] itself.
 */
sealed interface RecoveryAction {
    /** Attempting the exact same request again is worth offering. */
    data object Retry : RecoveryAction

    /** The request was rejected because of what the user submitted; retrying it verbatim won't help. */
    data object EditInput : RecoveryAction

    /** The server said the caller isn't authorized. This app has no login flow to send them to yet. */
    data object Authenticate : RecoveryAction

    /** Nothing left to do here; the only sane action is to leave the screen. */
    data object GoBack : RecoveryAction

    /**
     * The mutation may have already been applied and this app can't tell — see
     * [io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty.MAY_HAVE_REACHED_SERVER].
     * Offering [Retry] here risks a duplicate effect; this is the honest, infrastructure-free
     * stand-in for the full plan's `OutcomeUnknown` + reconciliation.
     */
    data object ContactSupport : RecoveryAction
}
