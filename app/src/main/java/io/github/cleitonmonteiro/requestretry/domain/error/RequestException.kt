package io.github.cleitonmonteiro.requestretry.domain.error

/** A data-layer failure with a stable classification boundary for the retry core. */
sealed class RequestException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause) {
    class Connection(cause: Throwable? = null) : RequestException("Connection failed", cause)

    class Http(val statusCode: Int, cause: Throwable? = null) :
        RequestException("HTTP request failed with status $statusCode", cause)
}
