package io.github.cleitonmonteiro.requestretry.data.remote

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps a Ktor or transport throwable to a typed [RequestFailureException]. Timeouts become
 * [RequestFailure.Generic], so the retry policy treats them as terminal.
 */
internal suspend fun Throwable.toRequestFailureException(): RequestFailureException {
    if (this is RequestFailureException) return this
    val failure = when (this) {
        is ResponseException -> RequestFailure.Http(
            statusCode = response.status.value,
            backendCode = response.headers["X-Error-Code"] ?: response.backendCodeFromBody(),
            requestId = response.headers["X-Request-ID"],
        )
        is UnknownHostException -> RequestFailure.Connection
        is SSLException -> RequestFailure.Generic
        is SerializationException -> RequestFailure.Generic
        is IOException -> timeoutOrConnectionFailure()
        else -> RequestFailure.Unknown
    }
    return RequestFailureException(failure, this)
}

private suspend fun io.ktor.client.statement.HttpResponse.backendCodeFromBody(): String? {
    val body = try {
        bodyAsText().take(MAX_ERROR_BODY_LENGTH)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (fatal: Error) {
        throw fatal
    } catch (_: Throwable) {
        return null
    }
    return runCatching {
        Json.parseToJsonElement(body).jsonObject["error_code"]?.jsonPrimitive?.content
    }.getOrNull()
}

private fun IOException.timeoutOrConnectionFailure(): RequestFailure {
    val name = javaClass.simpleName
    return if (name.contains("Timeout", ignoreCase = true)) {
        RequestFailure.Generic
    } else {
        RequestFailure.Connection
    }
}

private const val MAX_ERROR_BODY_LENGTH = 4_096
