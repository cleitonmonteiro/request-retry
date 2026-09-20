package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

object DefaultFailureClassifier : FailureClassifier {
    override fun classify(error: Throwable): RequestFailure {
        if (error is CancellationException) throw error
        return when (error) {
            is RequestFailureException -> normalize(error.failure)
            is UnknownHostException -> RequestFailure.Dns(error::class.simpleName)
            is SSLException -> RequestFailure.Tls(error::class.simpleName)
            is IllegalArgumentException -> RequestFailure.Local("invariant")
            is IOException -> RequestFailure.Connection(
                stage = TimeoutStage.CONNECT,
                outcomeCertainty = OutcomeCertainty.NOT_SENT,
                diagnosticCode = error::class.simpleName,
            )
            else -> RequestFailure.Unknown(error::class.simpleName ?: "unknown")
        }
    }

    private fun normalize(failure: RequestFailure): RequestFailure = when (failure) {
        is RequestFailure.Http -> when (failure.statusCode) {
            401 -> RequestFailure.AuthenticationRequired
            403 -> RequestFailure.PermissionDenied
            400, 422 -> RequestFailure.Validation(failure.backendCode)
            409 -> RequestFailure.Conflict(failure.backendCode)
            429 -> RequestFailure.RateLimited(failure.retryAfter)
            else -> failure
        }
        else -> failure
    }
}

data class RetryContext(
    val spec: OperationSpec,
    val failure: RequestFailure,
    val attempt: Int,
)

sealed interface RetryDecision {
    data class Retry(val reason: RetryReason, val serverDelay: Duration? = null) : RetryDecision
    data class Stop(val failure: PublicFailure, val recovery: RecoveryAction) : RetryDecision
    data object VerifyStatus : RetryDecision
}

fun interface RetryDecider {
    fun decide(context: RetryContext): RetryDecision
}

object ConservativeRetryDecider : RetryDecider {
    override fun decide(context: RetryContext): RetryDecision {
        val spec = context.spec
        val failure = context.failure
        if (isAmbiguousUnsafeMutation(failure, spec.safety)) return RetryDecision.VerifyStatus

        val terminal = terminalDecision(failure)
        if (terminal != null) return terminal

        val retryable = retryableDecision(failure)
        val canRetry = retryable != null &&
            spec.automaticRetry == AutomaticRetryPolicy.ENABLED &&
            context.attempt < spec.maxAttempts &&
            spec.safety !is OperationSafety.NonIdempotentCommand
        if (canRetry) return requireNotNull(retryable)

        if (isAmbiguousIdempotentMutation(failure, spec.safety)) return RetryDecision.VerifyStatus

        val publicFailure = publicFailure(failure)
        val recovery = if (
            spec.safety is OperationSafety.ReadOnly ||
            (failure == RequestFailure.Offline && spec.safety is OperationSafety.IdempotentCommand)
        ) RecoveryAction.Retry else RecoveryAction.Leave
        return RetryDecision.Stop(publicFailure, recovery)
    }

    private fun terminalDecision(failure: RequestFailure): RetryDecision.Stop? = when (failure) {
        RequestFailure.AuthenticationRequired -> RetryDecision.Stop(PublicFailure.AuthenticationRequired, RecoveryAction.Authenticate)
        RequestFailure.PermissionDenied -> RetryDecision.Stop(PublicFailure.PermissionDenied, RecoveryAction.Leave)
        is RequestFailure.Validation -> RetryDecision.Stop(PublicFailure.Validation, RecoveryAction.EditInput)
        is RequestFailure.Conflict -> RetryDecision.Stop(PublicFailure.Conflict, RecoveryAction.EditInput)
        is RequestFailure.Tls -> RetryDecision.Stop(PublicFailure.Protocol, RecoveryAction.ContactSupport)
        is RequestFailure.Protocol -> RetryDecision.Stop(PublicFailure.Protocol, RecoveryAction.ContactSupport)
        is RequestFailure.Local -> RetryDecision.Stop(PublicFailure.Local, RecoveryAction.ContactSupport)
        is RequestFailure.Unknown -> RetryDecision.Stop(PublicFailure.Unknown, RecoveryAction.Leave)
        is RequestFailure.Http -> if (failure.statusCode in 400..499 || failure.statusCode == 501) {
            if (failure.statusCode in setOf(408, 425, 429)) null
            else RetryDecision.Stop(publicFailure(failure), RecoveryAction.Leave)
        } else null
        else -> null
    }

    private fun retryableDecision(failure: RequestFailure): RetryDecision.Retry? = when (failure) {
        is RequestFailure.Dns,
        is RequestFailure.Connection,
        is RequestFailure.Timeout,
        -> RetryDecision.Retry(RetryReason.TransientTransport)
        is RequestFailure.RateLimited -> RetryDecision.Retry(RetryReason.RateLimited, failure.retryAfter)
        is RequestFailure.Http -> when (failure.statusCode) {
            408, 425 -> RetryDecision.Retry(RetryReason.TransientTransport, failure.retryAfter)
            429 -> RetryDecision.Retry(RetryReason.RateLimited, failure.retryAfter)
            500, 502, 503, 504 -> RetryDecision.Retry(RetryReason.ServerUnavailable, failure.retryAfter)
            else -> null
        }
        else -> null
    }

    private fun publicFailure(failure: RequestFailure): PublicFailure = when (failure) {
        RequestFailure.Offline -> PublicFailure.Offline
        RequestFailure.AuthenticationRequired -> PublicFailure.AuthenticationRequired
        RequestFailure.PermissionDenied -> PublicFailure.PermissionDenied
        is RequestFailure.Validation -> PublicFailure.Validation
        is RequestFailure.Conflict -> PublicFailure.Conflict
        is RequestFailure.RateLimited -> PublicFailure.RateLimited
        is RequestFailure.Protocol, is RequestFailure.Tls -> PublicFailure.Protocol
        is RequestFailure.Local -> PublicFailure.Local
        is RequestFailure.Timeout -> PublicFailure.TimedOut
        is RequestFailure.Dns, is RequestFailure.Connection -> PublicFailure.TemporarilyUnavailable
        is RequestFailure.Http -> when (failure.statusCode) {
            429 -> PublicFailure.RateLimited
            in 500..599 -> PublicFailure.TemporarilyUnavailable
            else -> PublicFailure.Unknown
        }
        is RequestFailure.Unknown -> PublicFailure.Unknown
    }

    private fun isAmbiguousUnsafeMutation(
        failure: RequestFailure,
        safety: OperationSafety,
    ): Boolean {
        if (safety !is OperationSafety.NonIdempotentCommand || !safety.statusVerificationAvailable) return false
        return when (failure) {
            is RequestFailure.Timeout -> failure.outcomeCertainty == OutcomeCertainty.MAY_HAVE_REACHED_SERVER
            is RequestFailure.Connection -> failure.outcomeCertainty == OutcomeCertainty.MAY_HAVE_REACHED_SERVER
            else -> false
        }
    }

    private fun isAmbiguousIdempotentMutation(
        failure: RequestFailure,
        safety: OperationSafety,
    ): Boolean {
        if (safety !is OperationSafety.IdempotentCommand || !safety.statusVerificationAvailable) return false
        return when (failure) {
            is RequestFailure.Timeout -> failure.outcomeCertainty == OutcomeCertainty.MAY_HAVE_REACHED_SERVER
            is RequestFailure.Connection -> failure.outcomeCertainty == OutcomeCertainty.MAY_HAVE_REACHED_SERVER
            else -> false
        }
    }
}
