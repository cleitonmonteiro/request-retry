package io.github.cleitonmonteiro.requestretry.domain.error

/**
 * Stable failure vocabulary shared by the data and resilience layers. Presentation never sees it
 * directly, only the sanitized public failure the retry policy maps it to.
 */
sealed interface RequestFailure {
    data object Offline : RequestFailure
    data object Connection : RequestFailure

    data class Http(
        val statusCode: Int,
        val backendCode: String? = null,
        val requestId: String? = null,
    ) : RequestFailure

    /**
     * Terminal failure whose exact cause (timeout, TLS, auth, permission, validation, conflict,
     * protocol) is not distinguished.
     */
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
