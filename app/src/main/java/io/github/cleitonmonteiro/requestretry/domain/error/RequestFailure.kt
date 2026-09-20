package io.github.cleitonmonteiro.requestretry.domain.error

import kotlin.time.Duration

/** Stable failure vocabulary shared by data, resilience, and presentation layers. */
sealed interface RequestFailure {
    data object Offline : RequestFailure
    data class Dns(val diagnosticCode: String? = null) : RequestFailure
    data class Tls(val diagnosticCode: String? = null) : RequestFailure
    data class Connection(
        val stage: TimeoutStage,
        val outcomeCertainty: OutcomeCertainty,
        val diagnosticCode: String? = null,
    ) : RequestFailure

    data class Timeout(
        val stage: TimeoutStage,
        val outcomeCertainty: OutcomeCertainty,
    ) : RequestFailure

    data class Http(
        val statusCode: Int,
        val backendCode: String? = null,
        val retryAfter: Duration? = null,
        val requestId: String? = null,
    ) : RequestFailure

    data object AuthenticationRequired : RequestFailure
    data object PermissionDenied : RequestFailure
    data class Validation(val backendCode: String? = null) : RequestFailure
    data class Conflict(val backendCode: String? = null) : RequestFailure
    data class RateLimited(val retryAfter: Duration? = null) : RequestFailure
    data class Protocol(val diagnosticCode: String) : RequestFailure
    data class Local(val diagnosticCode: String) : RequestFailure
    data class Unknown(val diagnosticCode: String) : RequestFailure
}

enum class TimeoutStage {
    CONNECT,
    REQUEST_BODY,
    RESPONSE_HEADERS,
    RESPONSE_BODY,
    OVERALL,
}

enum class OutcomeCertainty {
    NOT_SENT,
    MAY_HAVE_REACHED_SERVER,
    SERVER_REJECTED,
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
