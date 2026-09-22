package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import java.time.Instant
import kotlin.time.Duration

/** Stable names used to select a retry profile and label sanitized telemetry. */
enum class OperationName {
    PROFILE_READ,
    CREATE_ORDER,
}

/** Declares whether repeating an operation can be made safe by its stable identity. */
enum class OperationSafety { READ_ONLY, IDEMPOTENT_COMMAND }

/** Defines how a controller handles a start request while an operation is active. */
enum class ConcurrencyPolicy { CANCEL_PREVIOUS, DROP_WHILE_RUNNING }

/** Immutable, validated execution policy for one logical operation. */
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

/** Immutable context passed to a single call without exposing mutable controller state. */
data class AttemptContext(
    val operationId: OperationId,
    val operationName: OperationName,
    val attempt: Int,
    val maxAttempts: Int,
)

/** A single suspending HTTP operation executed from an immutable input snapshot. */
fun interface OneShotCall<in I, out O> {
    suspend fun execute(input: I, context: AttemptContext): O
}

/** UI-safe actions offered after a terminal or uncertain operation outcome. */
sealed interface RecoveryAction {
    data object Retry : RecoveryAction
    data object EditInput : RecoveryAction
    data object Authenticate : RecoveryAction
    /** Asks the UI to query the server for the supplied operation identity. */
    data class VerifyStatus(val operationId: OperationId) : RecoveryAction
    data object ContactSupport : RecoveryAction
    data object Leave : RecoveryAction
}

/** Sanitized failure categories that presentation may render. */
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

/** Sanitized reason for a cooldown before a manual retry becomes available. */
sealed interface RetryReason {
    data object TransientTransport : RetryReason
    data object ServerUnavailable : RetryReason
    data object RateLimited : RetryReason
}

/** Complete public lifecycle for one one-shot operation. */
sealed interface OperationState<out T> {
    data object Idle : OperationState<Nothing>

    /** A call is currently executing with the shown logical attempt number. */
    data class Running(
        val operationId: OperationId,
        val attempt: Int,
        val maxAttempts: Int,
        val startedAt: Instant,
    ) : OperationState<Nothing>

    /** A retryable failure is observing its cooldown before manual retry is offered. */
    data class BackingOff(
        val operationId: OperationId,
        val nextAttempt: Int,
        val maxAttempts: Int,
        val retryAt: Instant,
        val reason: RetryReason,
    ) : OperationState<Nothing>

    /** A call completed successfully with its immutable result. */
    data class Succeeded<T>(
        val operationId: OperationId,
        val data: T,
        val attemptsUsed: Int,
    ) : OperationState<T>

    /** An operation ended with sanitized feedback and an allowed recovery. */
    data class Failed(
        val operationId: OperationId,
        val failure: PublicFailure,
        val recovery: RecoveryAction,
        val attemptsUsed: Int,
    ) : OperationState<Nothing>

    /** A mutation may have reached the server and needs explicit status verification. */
    data class OutcomeUnknown(
        val operationId: OperationId,
        val recovery: RecoveryAction.VerifyStatus,
    ) : OperationState<Nothing>
}

/** Internal events emitted by the executor while a call is in progress. */
internal sealed interface ExecutionProgress {
    data class AttemptStarted(val attempt: Int) : ExecutionProgress
    data class RetryScheduled(
        val nextAttempt: Int,
        val delay: Duration,
        val reason: RetryReason,
    ) : ExecutionProgress
}

/** Internal terminal result returned by the executor to the controller. */
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

/** Converts an unexpected throwable into a stable [RequestFailure] for policy evaluation. */
fun interface FailureClassifier {
    fun classify(error: Throwable): RequestFailure
}
