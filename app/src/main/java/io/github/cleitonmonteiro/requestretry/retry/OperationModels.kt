package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import kotlin.time.Duration

/** Stable name used to select a retry profile and label sanitized telemetry. */
data class OperationName(val value: String)

/** Immutable, validated execution policy for one logical operation. */
data class OperationSpec(
    val name: OperationName,
    val maxAttempts: Int = 3,
    val backoff: BackoffStrategy = FullJitterBackoff(),
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
    data object Leave : RecoveryAction
}

/** Sanitized failure categories that presentation may render. */
sealed interface PublicFailure {
    data object Offline : PublicFailure
    data object TemporarilyUnavailable : PublicFailure
    data object Local : PublicFailure
    data object Unknown : PublicFailure
}

/** Complete public lifecycle for one one-shot operation. */
sealed interface OperationState<out T> {
    data object Idle : OperationState<Nothing>

    /** A call is currently executing with the shown logical attempt number. */
    data class Running(
        val attempt: Int,
        val maxAttempts: Int,
    ) : OperationState<Nothing>

    /** A call completed successfully with its immutable result. */
    data class Succeeded<T>(
        val data: T,
        val attemptsUsed: Int,
    ) : OperationState<T>

    /** An operation ended with sanitized feedback and an allowed recovery. */
    data class Failed(
        val failure: PublicFailure,
        val recovery: RecoveryAction,
        val attemptsUsed: Int,
    ) : OperationState<Nothing>
}

/** Internal terminal result returned by the executor to the controller. */
internal sealed interface ExecutionOutcome<out T> {
    data class Success<T>(val data: T, val attemptsUsed: Int) : ExecutionOutcome<T>

    /** [pendingRetry] is the cooldown to spend before the next manual attempt's call fires. */
    data class ManualRetry(
        val failure: PublicFailure,
        val attemptsUsed: Int,
        val pendingRetry: Duration,
    ) : ExecutionOutcome<Nothing>
    data class Failure(
        val failure: PublicFailure,
        val recovery: RecoveryAction,
        val attemptsUsed: Int,
    ) : ExecutionOutcome<Nothing>
}

/** Converts an unexpected throwable into a stable [RequestFailure] for policy evaluation. */
fun interface FailureClassifier {
    fun classify(error: Throwable): RequestFailure
}
