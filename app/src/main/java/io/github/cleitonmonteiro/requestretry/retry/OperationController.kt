package io.github.cleitonmonteiro.requestretry.retry

import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Creates the immutable operation policy from the submitted input snapshot. */
fun interface OperationSpecFactory<in I> {
    fun create(input: I): OperationSpec
}

/** Result of asking the server to confirm an operation with an uncertain outcome. */
sealed interface VerificationResult<out T> {
    data class Confirmed<T>(val data: T) : VerificationResult<T>
    data class Rejected(val failure: PublicFailure, val recovery: RecoveryAction) : VerificationResult<Nothing>
    data object StillProcessing : VerificationResult<Nothing>
    data object Unknown : VerificationResult<Nothing>
}

/** Confirms an ambiguous remote mutation without issuing the mutation again. */
fun interface StatusVerifier<in I, out O> {
    suspend fun verify(input: I): VerificationResult<O>
}

/** Provides wall-clock time so controller state remains testable. */
fun interface WallClock {
    fun now(): Instant
}

/** Production [WallClock] backed by the system clock. */
object SystemWallClock : WallClock {
    override fun now(): Instant = Instant.now()
}

/**
 * Serializes all public commands and execution events through one mailbox. The input stored in a
 * session is immutable, so retries and status verification can never observe later UI edits.
 */
class OperationController<I, O>(
    private val scope: CoroutineScope,
    private val specFactory: OperationSpecFactory<I>,
    private val executor: RetryExecutor,
    private val call: OneShotCall<I, O>,
    private val verifier: StatusVerifier<I, O>? = null,
    private val wallClock: WallClock = SystemWallClock,
) {
    /** Immutable command snapshot that prevents retries from observing later form edits. */
    private data class Session<I>(
        val token: Long,
        val input: I,
        val spec: OperationSpec,
        val attempt: Int,
    )

    private val _state = MutableStateFlow<OperationState<O>>(OperationState.Idle)
    val state: StateFlow<OperationState<O>> = _state.asStateFlow()

    private val mailbox = Channel<suspend () -> Unit>(capacity = 64)
    private var nextToken = 0L
    private var current: Session<I>? = null
    private var executionJob: Job? = null

    init {
        scope.launch {
            for (command in mailbox) command()
        }
    }

    fun start(input: I) {
        mailbox.trySend { handleStart(input) }
    }

    fun retry() {
        mailbox.trySend {
            if (executionJob?.isActive == true) return@trySend
            val failed = _state.value as? OperationState.Failed ?: return@trySend
            if (failed.recovery != RecoveryAction.Retry) return@trySend
            val session = current ?: return@trySend
            begin(session.input, session.spec, failed.attemptsUsed + 1)
        }
    }

    fun reset() {
        mailbox.trySend {
            if (executionJob?.isActive == true) return@trySend
            current = null
            _state.value = OperationState.Idle
        }
    }

    fun verifyStatus() {
        mailbox.trySend {
            val unknown = _state.value as? OperationState.OutcomeUnknown ?: return@trySend
            val session = current ?: return@trySend
            val statusVerifier = verifier ?: return@trySend
            val token = session.token
            _state.value = OperationState.Running(
                operationId = unknown.operationId,
                attempt = 1,
                maxAttempts = 1,
                startedAt = wallClock.now(),
            )
            executionJob = scope.launch {
                val result = try {
                    statusVerifier.verify(session.input)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    VerificationResult.Unknown
                }
                mailbox.send { handleVerification(token, result) }
            }
        }
    }

    private fun handleStart(input: I) {
        val active = executionJob?.isActive == true
        if (!active) {
            begin(input, specFactory.create(input), attempt = 1)
            return
        }
        executionJob?.cancel()
        begin(input, specFactory.create(input), attempt = 1)
    }

    private fun begin(input: I, spec: OperationSpec, attempt: Int) {
        val session = Session(++nextToken, input, spec, attempt)
        current = session
        _state.value = OperationState.Running(
            operationId = spec.operationId,
            attempt = attempt,
            maxAttempts = spec.maxAttempts,
            startedAt = wallClock.now(),
        )
        executionJob = scope.launch {
            val outcome = executor.execute(input, spec, attempt, call) { progress ->
                mailbox.send { publishProgress(session.token, progress) }
            }
            mailbox.send { publishOutcome(session.token, outcome) }
        }
    }

    private fun publishProgress(token: Long, progress: ExecutionProgress) {
        val session = current?.takeIf { it.token == token } ?: return
        _state.value = when (progress) {
            is ExecutionProgress.AttemptStarted -> OperationState.Running(
                operationId = session.spec.operationId,
                attempt = progress.attempt,
                maxAttempts = session.spec.maxAttempts,
                startedAt = wallClock.now(),
            )
            is ExecutionProgress.RetryScheduled -> OperationState.BackingOff(
                operationId = session.spec.operationId,
                nextAttempt = progress.nextAttempt,
                maxAttempts = session.spec.maxAttempts,
                retryAt = wallClock.now().plusMillis(progress.delay.inWholeMilliseconds),
                reason = progress.reason,
            )
        }
    }

    private fun publishOutcome(token: Long, outcome: ExecutionOutcome<O>) {
        val session = current?.takeIf { it.token == token } ?: return
        _state.value = when (outcome) {
            is ExecutionOutcome.Success -> OperationState.Succeeded(
                operationId = session.spec.operationId,
                data = outcome.data,
                attemptsUsed = outcome.attemptsUsed,
            )
            is ExecutionOutcome.ManualRetry -> OperationState.Failed(
                operationId = session.spec.operationId,
                failure = outcome.failure,
                recovery = RecoveryAction.Retry,
                attemptsUsed = outcome.attemptsUsed,
            )
            is ExecutionOutcome.Failure -> OperationState.Failed(
                operationId = session.spec.operationId,
                failure = outcome.failure,
                recovery = outcome.recovery,
                attemptsUsed = outcome.attemptsUsed,
            )
            is ExecutionOutcome.Unknown -> OperationState.OutcomeUnknown(
                operationId = outcome.operationId,
                recovery = RecoveryAction.VerifyStatus(outcome.operationId),
            )
        }
        executionJob = null
    }

    private fun handleVerification(token: Long, result: VerificationResult<O>) {
        val session = current?.takeIf { it.token == token } ?: return
        _state.value = when (result) {
            is VerificationResult.Confirmed -> OperationState.Succeeded(
                operationId = session.spec.operationId,
                data = result.data,
                attemptsUsed = 1,
            )
            is VerificationResult.Rejected -> OperationState.Failed(
                operationId = session.spec.operationId,
                failure = result.failure,
                recovery = result.recovery,
                attemptsUsed = 1,
            )
            VerificationResult.StillProcessing,
            VerificationResult.Unknown,
            -> OperationState.OutcomeUnknown(
                operationId = session.spec.operationId,
                recovery = RecoveryAction.VerifyStatus(session.spec.operationId),
            )
        }
        executionJob = null
    }
}
