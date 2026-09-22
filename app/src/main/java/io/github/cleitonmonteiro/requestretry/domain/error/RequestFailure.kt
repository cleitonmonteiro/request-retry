package io.github.cleitonmonteiro.requestretry.domain.error

import kotlin.time.Duration

/** Stable failure vocabulary shared by data, resilience, and presentation layers. */
sealed interface RequestFailure {
    data object Offline : RequestFailure
    data class Connection(val mayHaveReachedServer: Boolean) : RequestFailure

    data class Http(
        val statusCode: Int,
        val backendCode: String? = null,
        val retryAfter: Duration? = null,
        val requestId: String? = null,
    ) : RequestFailure

    /** Terminal failure whose exact cause (timeout, auth, permission, validation, conflict, rate limit, protocol) is not distinguished. */
    data object Generic : RequestFailure
    data object Local : RequestFailure
    data object Unknown : RequestFailure
}

/**
 * Internal transport for a typed failure through Flow's exception channel.
 *
 * The cause is intentionally not part of [RequestFailure], state equality, or user-facing copy.
 */
class RequestFailureException(
    val failure: RequestFailure,
    cause: Throwable? = null,
) : java.io.IOException("Request failed: ${failure::class.simpleName}", cause)
