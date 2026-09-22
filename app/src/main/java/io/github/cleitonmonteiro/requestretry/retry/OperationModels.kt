package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import java.time.Instant
import kotlin.time.Duration

enum class OperationName {
    PROFILE_READ,
    CREATE_ORDER,
}

enum class OperationSafety { READ_ONLY, IDEMPOTENT_COMMAND }

enum class ConcurrencyPolicy { CANCEL_PREVIOUS, DROP_WHILE_RUNNING }

data class OperationSpec(
    val operationId: OperationId,
    val name: OperationName,
    val safety: OperationSafety,
    val maxAttempts: Int,
    val backoff: BackoffStrategy,
    val concurrency: ConcurrencyPolicy,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be at least 1" }
        require(backoff.maxDelay.isFinite() && !backoff.maxDelay.isNegative()) {
            "backoff maxDelay must be finite and non-negative"
        }
    }
}

data class AttemptContext(
    val operationId: OperationId,
    val operationName: OperationName,
    val attempt: Int,
    val maxAttempts: Int,
)

fun interface OneShotCall<in I, out O> {
    suspend fun execute(input: I, context: AttemptContext): O
}

sealed interface RecoveryAction {
    data object Retry : RecoveryAction
    data object EditInput : RecoveryAction
    data object Authenticate : RecoveryAction
    data class VerifyStatus(val operationId: OperationId) : RecoveryAction
    data object ContactSupport : RecoveryAction
    data object Leave : RecoveryAction
}

sealed interface PublicFailure {
    data object Offline : PublicFailure
    data object TemporarilyUnavailable : PublicFailure
    data object AuthenticationRequired : PublicFailure
    data object PermissionDenied : PublicFailure
    data object Validation : PublicFailure
    data object Conflict : PublicFailure
    data object RateLimited : PublicFailure
    data object Protocol : PublicFailure
    data object Local : PublicFailure
    data object TimedOut : PublicFailure
    data object Unknown : PublicFailure
}

sealed interface RetryReason {
    data object TransientTransport : RetryReason
    data object ServerUnavailable : RetryReason
    data object RateLimited : RetryReason
}

sealed interface OperationState<out T> {
    data object Idle : OperationState<Nothing>

    data class Running(
        val operationId: OperationId,
        val attempt: Int,
        val maxAttempts: Int,
        val startedAt: Instant,
    ) : OperationState<Nothing>

    data class BackingOff(
        val operationId: OperationId,
        val nextAttempt: Int,
        val maxAttempts: Int,
        val retryAt: Instant,
        val reason: RetryReason,
    ) : OperationState<Nothing>

    data class Succeeded<T>(
        val operationId: OperationId,
        val data: T,
        val attemptsUsed: Int,
    ) : OperationState<T>

    data class Failed(
        val operationId: OperationId,
        val failure: PublicFailure,
        val recovery: RecoveryAction,
        val attemptsUsed: Int,
    ) : OperationState<Nothing>

    data class OutcomeUnknown(
        val operationId: OperationId,
        val recovery: RecoveryAction.VerifyStatus,
    ) : OperationState<Nothing>
}

internal sealed interface ExecutionProgress {
    data class AttemptStarted(val attempt: Int) : ExecutionProgress
    data class RetryScheduled(
        val nextAttempt: Int,
        val delay: Duration,
        val reason: RetryReason,
    ) : ExecutionProgress
}

internal sealed interface ExecutionOutcome<out T> {
    data class Success<T>(val data: T, val attemptsUsed: Int) : ExecutionOutcome<T>
    data class ManualRetry(
        val failure: PublicFailure,
        val attemptsUsed: Int,
    ) : ExecutionOutcome<Nothing>
    data class Failure(
        val failure: PublicFailure,
        val recovery: RecoveryAction,
        val attemptsUsed: Int,
    ) : ExecutionOutcome<Nothing>
    data class Unknown(val operationId: OperationId) : ExecutionOutcome<Nothing>
}

fun interface FailureClassifier {
    fun classify(error: Throwable): RequestFailure
}
