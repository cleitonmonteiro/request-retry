package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

/**
 * Converts thrown errors into the app's stable [RequestFailure] vocabulary, collapsing HTTP 400,
 * 401, 403, 409, and 422 into [RequestFailure.Generic]. The executor never passes the cancellation
 * of its own coroutine here, so a [CancellationException] that does arrive, such as a `withTimeout`
 * inside the call, fails closed as [RequestFailure.Unknown].
 */
object DefaultFailureClassifier : FailureClassifier {
    override fun classify(error: Throwable): RequestFailure = when (error) {
        is RequestFailureException -> normalize(error.failure)
        is UnknownHostException -> RequestFailure.Connection
        is SSLException -> RequestFailure.Generic
        is IllegalArgumentException -> RequestFailure.Local
        is IOException -> RequestFailure.Connection
        else -> RequestFailure.Unknown
    }

    private fun normalize(failure: RequestFailure): RequestFailure = when (failure) {
        is RequestFailure.Http -> when (failure.statusCode) {
            400, 401, 403, 409, 422 -> RequestFailure.Generic
            else -> failure
        }
        else -> failure
    }
}

/** Inputs for deciding whether a failed attempt can be retried; [attempt] is 1-based. */
data class RetryContext(
    val spec: OperationSpec,
    val failure: RequestFailure,
    val attempt: Int,
)

/** Result of applying the retry policy to one classified failure. */
sealed interface RetryDecision {
    /** Offers a manual next attempt; the executor derives its cooldown from the spec's backoff. */
    data class Retry(val failure: PublicFailure) : RetryDecision

    /** Ends the operation with safe public feedback and a recovery action. */
    data class Stop(val failure: PublicFailure, val recovery: RecoveryAction) : RetryDecision
}

/** Applies operation safety and attempt limits to a classified failure. */
fun interface RetryDecider {
    fun decide(context: RetryContext): RetryDecision
}

/**
 * Fail-closed policy that allows retries only for explicitly transient failures, within
 * [OperationSpec.maxAttempts]. Once attempts are exhausted, only Offline still offers
 * [RecoveryAction.Retry].
 */
object ConservativeRetryDecider : RetryDecider {
    override fun decide(context: RetryContext): RetryDecision {
        val spec = context.spec
        val failure = context.failure
        val terminal = terminalDecision(failure)
        if (terminal != null) return terminal

        val retryable = retryableDecision(failure)
        if (retryable != null && context.attempt < spec.maxAttempts) return retryable

        val recovery = if (failure == RequestFailure.Offline) RecoveryAction.Retry else RecoveryAction.Leave
        return RetryDecision.Stop(publicFailure(failure), recovery)
    }

    private fun terminalDecision(failure: RequestFailure): RetryDecision.Stop? = when (failure) {
        RequestFailure.Generic -> RetryDecision.Stop(PublicFailure.Unknown, RecoveryAction.Leave)
        RequestFailure.Local -> RetryDecision.Stop(PublicFailure.Local, RecoveryAction.Leave)
        RequestFailure.Unknown -> RetryDecision.Stop(PublicFailure.Unknown, RecoveryAction.Leave)
        is RequestFailure.Http -> if (failure.statusCode in 400..499 || failure.statusCode == 501) {
            if (failure.statusCode in setOf(408, 425, 429)) null
            else RetryDecision.Stop(publicFailure(failure), RecoveryAction.Leave)
        } else null
        else -> null
    }

    private fun retryableDecision(failure: RequestFailure): RetryDecision.Retry? = when (failure) {
        RequestFailure.Offline,
        RequestFailure.Connection,
        -> RetryDecision.Retry(publicFailure(failure))
        is RequestFailure.Http -> when (failure.statusCode) {
            408, 425, 429, 500, 502, 503, 504 -> RetryDecision.Retry(publicFailure(failure))
            else -> null
        }
        else -> null
    }

    private fun publicFailure(failure: RequestFailure): PublicFailure = when (failure) {
        RequestFailure.Offline -> PublicFailure.Offline
        RequestFailure.Generic -> PublicFailure.Unknown
        RequestFailure.Local -> PublicFailure.Local
        RequestFailure.Connection -> PublicFailure.TemporarilyUnavailable
        is RequestFailure.Http -> when (failure.statusCode) {
            in 500..599 -> PublicFailure.TemporarilyUnavailable
            else -> PublicFailure.Unknown
        }
        RequestFailure.Unknown -> PublicFailure.Unknown
    }
}
