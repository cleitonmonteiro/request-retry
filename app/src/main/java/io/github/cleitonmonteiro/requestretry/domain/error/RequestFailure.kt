package io.github.cleitonmonteiro.requestretry.domain.error

import java.io.IOException
import kotlin.time.Duration

/**
 * Stable, data-only classification of a request failure — the boundary [retry] decides against.
 * Deliberately data, not exceptions: it never carries a stack trace or the original cause into
 * equality/state, so it's safe to hold in a [io.github.cleitonmonteiro.requestretry.retry.RetryUiState]
 * and compare in tests. [Http.message] is the one deliberate exception to "no text here" — the
 * server's own validation text for a `422` is real, structured information the client can't
 * reconstruct locally. See `planos/plano-basico-evolucao-retry.md` §6.
 */
sealed interface RequestFailure {

    /** No network path was even attempted — e.g. [android.net.ConnectivityManager] reports no link. */
    data object Offline : RequestFailure

    /**
     * A transport-level failure (socket reset, DNS, TLS, connection refused, …) that isn't a
     * timeout. [certainty] is what a retry decision actually turns on.
     */
    data class Connection(val certainty: OutcomeCertainty) : RequestFailure

    /** A configured deadline elapsed. [stage] says which one; [OVERALL] is always terminal. */
    data class Timeout(
        val stage: TimeoutStage,
        val certainty: OutcomeCertainty,
    ) : RequestFailure

    /**
     * A real HTTP response came back with a non-2xx status. [message] is the server's own error
     * text (e.g. its `{"error": "..."}` body) when one was available — used verbatim as the
     * `Feedback` description for a `422`, since a validation failure's specifics come from the
     * server, not from a generic client-side string (see `ui/components/RetryPresentation.kt`).
     * Every other status still gets its copy from `strings.xml`, [message] or not: a general
     * business/backend code isn't safe to show as-is, but a validation reason is exactly what
     * the user needs to act on.
     */
    data class Http(
        val statusCode: Int,
        val retryAfter: Duration? = null,
        val requestId: String? = null,
        val message: String? = null,
    ) : RequestFailure

    /** The response couldn't be parsed into the expected shape — repeating won't fix it. */
    data class Protocol(val diagnosticCode: String) : RequestFailure

    /** Anything the classifier didn't recognize. Always terminal — the default is fail closed. */
    data class Unknown(val diagnosticCode: String) : RequestFailure
}

/** Which stage of a request a [RequestFailure.Timeout] elapsed during. */
enum class TimeoutStage { CONNECT, REQUEST_BODY, RESPONSE, OVERALL }

/**
 * Whether the request might have reached the server — the client-side safety signal for whether
 * repeating it is safe, since this project intentionally doesn't evolve the idempotency key
 * contract (see §1.1 of the plan). A mutation with [MAY_HAVE_REACHED_SERVER] is never repeated
 * automatically or manually — see [io.github.cleitonmonteiro.requestretry.retry.DefaultRetryDecider].
 */
enum class OutcomeCertainty {
    /** The request comprovadamente did not leave the client: safe to repeat any operation. */
    NOT_SENT,
    /** The request may have reached the server: only safe to repeat a read. */
    MAY_HAVE_REACHED_SERVER,
}

/**
 * Bridges [RequestFailure] data to the [Throwable] world [kotlinx.coroutines.flow.Flow.catch]
 * needs — the data layer throws this; nothing above [io.github.cleitonmonteiro.requestretry.retry.RetryExecutor]
 * ever inspects [cause] or [message].
 */
class RequestFailureException(val failure: RequestFailure, cause: Throwable? = null) :
    IOException(failure.toString(), cause)
