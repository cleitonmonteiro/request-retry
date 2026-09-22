package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

/** Converts thrown transport errors into the app's stable [RequestFailure] vocabulary. */
object DefaultFailureClassifier : FailureClassifier {
    override fun classify(error: Throwable): RequestFailure {
        if (error is CancellationException) throw error
        return when (error) {
            is RequestFailureException -> normalize(error.failure)
            is UnknownHostException -> RequestFailure.Connection(mayHaveReachedServer = false)
            is SSLException -> RequestFailure.Generic
            is IllegalArgumentException -> RequestFailure.Local
            is IOException -> RequestFailure.Connection(mayHaveReachedServer = false)
            else -> RequestFailure.Unknown
        }
    }

    private fun normalize(failure: RequestFailure): RequestFailure = when (failure) {
        is RequestFailure.Http -> when (failure.statusCode) {
            400, 401, 403, 409, 422, 429 -> RequestFailure.Generic
            else -> failure
        }
        else -> failure
    }
}

/** Inputs used to decide whether an unsuccessful logical attempt can be retried. */
data class RetryContext(
    val spec: OperationSpec,
    val failure: RequestFailure,
    val attempt: Int,
)

/** Internal result of applying the retry policy to one classified failure. */
sealed interface RetryDecision {
    /** Permits a user-initiated next attempt after the requested cooldown. */
    data class Retry(
        val failure: PublicFailure,
        val reason: RetryReason,
        val serverDelay: Duration? = null,
    ) : RetryDecision
    /** Ends the operation with safe public feedback and a recovery action. */
    data class Stop(val failure: PublicFailure, val recovery: RecoveryAction) : RetryDecision
    data object VerifyStatus : RetryDecision
}

/** Applies operation safety and attempt limits to a classified failure. */
fun interface RetryDecider {
    fun decide(context: RetryContext): RetryDecision
}

/** Fail-closed policy that allows retries only for explicitly transient failures. */
object ConservativeRetryDecider : RetryDecider {
    override fun decide(context: RetryContext): RetryDecision {
        val spec = context.spec
        val failure = context.failure
        val terminal = terminalDecision(failure)
        if (terminal != null) return terminal

        val retryable = retryableDecision(failure)
        if (retryable != null) {
            if (context.attempt < spec.maxAttempts) return retryable
            if (isAmbiguousIdempotentMutation(failure, spec.safety)) return RetryDecision.VerifyStatus
            return RetryDecision.Stop(publicFailure(failure), RecoveryAction.Leave)
        }

        if (isAmbiguousIdempotentMutation(failure, spec.safety)) return RetryDecision.VerifyStatus

        val publicFailure = publicFailure(failure)
        val recovery = if (
            spec.safety == OperationSafety.READ_ONLY ||
            (failure == RequestFailure.Offline && spec.safety == OperationSafety.IDEMPOTENT_COMMAND)
        ) RecoveryAction.Retry else RecoveryAction.Leave
        return RetryDecision.Stop(publicFailure, recovery)
    }

    private fun terminalDecision(failure: RequestFailure): RetryDecision.Stop? = when (failure) {
        RequestFailure.Generic -> RetryDecision.Stop(PublicFailure.Unknown, RecoveryAction.Leave)
        RequestFailure.Local -> RetryDecision.Stop(PublicFailure.Local, RecoveryAction.ContactSupport)
        RequestFailure.Unknown -> RetryDecision.Stop(PublicFailure.Unknown, RecoveryAction.Leave)
        is RequestFailure.Http -> if (failure.statusCode in 400..499 || failure.statusCode == 501) {
            if (failure.statusCode in setOf(408, 425, 429)) null
            else RetryDecision.Stop(publicFailure(failure), RecoveryAction.Leave)
        } else null
        else -> null
    }

    private fun retryableDecision(failure: RequestFailure): RetryDecision.Retry? = when (failure) {
        RequestFailure.Offline,
        is RequestFailure.Connection,
        -> RetryDecision.Retry(publicFailure(failure), RetryReason.TransientTransport)
        is RequestFailure.Http -> when (failure.statusCode) {
            408, 425 -> RetryDecision.Retry(
                publicFailure(failure),
                RetryReason.TransientTransport,
                failure.retryAfter,
            )
            429 -> RetryDecision.Retry(publicFailure(failure), RetryReason.RateLimited, failure.retryAfter)
            500, 502, 503, 504 -> RetryDecision.Retry(
                publicFailure(failure),
                RetryReason.ServerUnavailable,
                failure.retryAfter,
            )
            else -> null
        }
        else -> null
    }

    private fun publicFailure(failure: RequestFailure): PublicFailure = when (failure) {
        RequestFailure.Offline -> PublicFailure.Offline
        RequestFailure.Generic -> PublicFailure.Unknown
        RequestFailure.Local -> PublicFailure.Local
        is RequestFailure.Connection -> PublicFailure.TemporarilyUnavailable
        is RequestFailure.Http -> when (failure.statusCode) {
            in 500..599 -> PublicFailure.TemporarilyUnavailable
            else -> PublicFailure.Unknown
        }
        RequestFailure.Unknown -> PublicFailure.Unknown
    }

    private fun isAmbiguousIdempotentMutation(
        failure: RequestFailure,
        safety: OperationSafety,
    ): Boolean {
        if (safety != OperationSafety.IDEMPOTENT_COMMAND) return false
        return when (failure) {
            is RequestFailure.Connection -> failure.mayHaveReachedServer
            else -> false
        }
    }
}
