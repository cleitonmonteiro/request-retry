package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage

/**
 * Turns a classified [RequestFailure] into a [RetryDecision]: this app's retries are always
 * user-triggered by tapping the [RecoveryAction.Retry] button [RetryStateScaffold] renders (see
 * `planos/plano-basico-evolucao-retry.md` §10.4 — there's never a fully-silent auto-retry loop),
 * so the only question this interface answers is *whether* that button is offered, and if not,
 * which [RecoveryAction] replaces it.
 */
fun interface RetryDecider {
    fun decide(failure: RequestFailure, spec: OperationSpec, attempt: Int): RetryDecision
}

/**
 * The default decision table — §7 of the plan. The default is always to *not* repeat: a failure
 * only earns [RecoveryAction.Retry] when it's explicitly listed as retryable for the operation's
 * [OperationKind], and the budget isn't spent. A command whose outcome is merely
 * [OutcomeCertainty.MAY_HAVE_REACHED_SERVER] is always terminal — this app doesn't distinguish
 * commands by idempotency, so the conservative default applies uniformly. Whatever server text a
 * [RequestFailure.Http] carried is passed straight through as [RetryDecision.serverMessage],
 * unclassified — see that failure's own KDoc for why a `422` is the deliberate exception to this
 * app's "no raw backend text" rule.
 */
object DefaultRetryDecider : RetryDecider {

    override fun decide(failure: RequestFailure, spec: OperationSpec, attempt: Int): RetryDecision {
        val ambiguous = isAmbiguousMutation(failure, spec)
        val publicFailure = toPublicFailure(failure, ambiguous)
        val recovery = if (attempt < spec.maxAttempts && isRetryable(failure, spec)) {
            RecoveryAction.Retry
        } else {
            terminalActionFor(failure, ambiguous)
        }
        val serverMessage = (failure as? RequestFailure.Http)?.message
        return RetryDecision(publicFailure, recovery, serverMessage)
    }

    private fun isRetryable(failure: RequestFailure, spec: OperationSpec): Boolean = when (failure) {
        RequestFailure.Offline -> true
        is RequestFailure.Connection -> allowsRepeat(failure.certainty, spec)
        is RequestFailure.Timeout -> when (failure.stage) {
            TimeoutStage.OVERALL -> false
            else -> allowsRepeat(failure.certainty, spec)
        }
        is RequestFailure.Http -> when (failure.statusCode) {
            408, 425, 429 -> true
            in 500..599 -> allowsRepeat(OutcomeCertainty.MAY_HAVE_REACHED_SERVER, spec)
            else -> false
        }
        is RequestFailure.Protocol -> false
        is RequestFailure.Unknown -> false
    }

    /** A [OutcomeCertainty.NOT_SENT] failure is always safe to repeat; a
     * [OutcomeCertainty.MAY_HAVE_REACHED_SERVER] one only for a read. */
    private fun allowsRepeat(certainty: OutcomeCertainty, spec: OperationSpec): Boolean = when (certainty) {
        OutcomeCertainty.NOT_SENT -> true
        OutcomeCertainty.MAY_HAVE_REACHED_SERVER -> spec.kind == OperationKind.READ
    }

    private fun terminalActionFor(failure: RequestFailure, ambiguous: Boolean): RecoveryAction {
        if (ambiguous) return RecoveryAction.ContactSupport
        return when (failure) {
            is RequestFailure.Http -> when (failure.statusCode) {
                401, 403 -> RecoveryAction.Authenticate
                422 -> RecoveryAction.EditInput
                else -> RecoveryAction.GoBack
            }
            else -> RecoveryAction.GoBack
        }
    }

    /** A command whose effect may already have been applied. */
    private fun isAmbiguousMutation(failure: RequestFailure, spec: OperationSpec): Boolean {
        if (spec.kind != OperationKind.COMMAND) return false
        return when (failure) {
            is RequestFailure.Connection -> failure.certainty == OutcomeCertainty.MAY_HAVE_REACHED_SERVER
            is RequestFailure.Timeout ->
                failure.certainty == OutcomeCertainty.MAY_HAVE_REACHED_SERVER && failure.stage != TimeoutStage.OVERALL
            is RequestFailure.Http -> failure.statusCode in 500..599
            else -> false
        }
    }

    private fun toPublicFailure(failure: RequestFailure, ambiguous: Boolean): PublicFailure {
        if (ambiguous) return PublicFailure.AMBIGUOUS_MUTATION
        return when (failure) {
            RequestFailure.Offline -> PublicFailure.OFFLINE
            is RequestFailure.Connection -> PublicFailure.CONNECTION
            is RequestFailure.Timeout -> PublicFailure.TIMEOUT
            is RequestFailure.Http -> when (failure.statusCode) {
                401, 403 -> PublicFailure.PERMISSION
                404 -> PublicFailure.NOT_FOUND
                409 -> PublicFailure.CONFLICT
                422 -> PublicFailure.VALIDATION
                429 -> PublicFailure.RATE_LIMITED
                in 500..599 -> PublicFailure.SERVER
                else -> PublicFailure.UNKNOWN
            }
            is RequestFailure.Protocol -> PublicFailure.PROTOCOL
            is RequestFailure.Unknown -> PublicFailure.UNKNOWN
        }
    }
}
