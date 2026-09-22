package io.github.cleitonmonteiro.requestretry.data.remote

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import java.io.IOException
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun Throwable.toRequestFailureException(now: Instant = Instant.now()): RequestFailureException {
    if (this is RequestFailureException) return this
    val failure = when (this) {
        is ResponseException -> RequestFailure.Http(
            statusCode = response.status.value,
            backendCode = response.headers["X-Error-Code"] ?: response.backendCodeFromBody(),
            retryAfter = parseRetryAfter(response.headers["Retry-After"], now),
            requestId = response.headers["X-Request-ID"],
        )
        is UnknownHostException -> RequestFailure.Connection(mayHaveReachedServer = false)
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
        RequestFailure.Connection(mayHaveReachedServer = false)
    }
}

internal fun parseRetryAfter(
    value: String?,
    now: Instant = Instant.now(),
    maximum: Duration = 30.seconds,
): Duration? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val seconds = normalized.toLongOrNull()?.takeIf { it >= 0 }
    val duration = if (seconds != null) {
        seconds.seconds
    } else {
        runCatching {
            val retryAt = ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            (retryAt.epochSecond - now.epochSecond).coerceAtLeast(0).seconds
        }.getOrNull()
    } ?: return null
    return duration.coerceAtMost(maximum)
}

private const val MAX_ERROR_BODY_LENGTH = 4_096
