package io.github.cleitonmonteiro.requestretry.retry

/**
 * A stable, text-free classification of a failure for the UI to render — the payload of
 * [RetryUiState.Feedback]. Deliberately an enum, not the richer
 * [io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure]: the presentation layer
 * maps each entry to localized copy (see `ui/components/RetryPresentation.kt`), and an enum
 * keeps that mapping exhaustive and easy to audit for missing strings.
 */
enum class PublicFailure {
    OFFLINE,
    CONNECTION,
    TIMEOUT,
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    PERMISSION,
    RATE_LIMITED,
    SERVER,
    PROTOCOL,

    /** The mutation may have already been applied — see [RecoveryAction.ContactSupport]. */
    AMBIGUOUS_MUTATION,
    UNKNOWN,
}
