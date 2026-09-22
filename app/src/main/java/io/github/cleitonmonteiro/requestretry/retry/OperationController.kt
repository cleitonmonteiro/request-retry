package io.github.cleitonmonteiro.requestretry.retry

import java.time.Instant
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
    private val wallClock: WallClock = SystemWallClock,
) {
    /** Immutable command snapshot that prevents retries from observing later form edits. */
    private class Session<I>(
        val token: Long,
        val input: I,
        val spec: OperationSpec,
        val attempt: Int,
    ) {
        lateinit var job: Job
    }

    private val _state = MutableStateFlow<OperationState<O>>(OperationState.Idle)
    val state: StateFlow<OperationState<O>> = _state.asStateFlow()

    private val mailbox = Channel<suspend () -> Unit>(capacity = 64)
    private var nextToken = 0L
    private var current: Session<I>? = null

    init {
        scope.launch {
            for (command in mailbox) command()
        }
    }

    fun start(input: I) {
        check(mailbox.trySend { handleStart(input) }.isSuccess) { "Operation mailbox is full" }
    }

    fun retry() {
        check(
            mailbox.trySend {
                if (current?.job?.isActive == true) return@trySend
                val failed = _state.value as? OperationState.Failed ?: return@trySend
                if (failed.recovery != RecoveryAction.Retry) return@trySend
                val session = current ?: return@trySend
                begin(session.input, session.spec, failed.attemptsUsed + 1)
            }.isSuccess,
        ) { "Operation mailbox is full" }
    }

    private fun handleStart(input: I) {
        current?.job?.takeIf { it.isActive }?.cancel()
        begin(input, specFactory.create(input), attempt = 1)
    }

    private fun begin(input: I, spec: OperationSpec, attempt: Int) {
        val session = Session(++nextToken, input, spec, attempt)
        current = session
        _state.value = OperationState.Running(
            attempt = attempt,
            maxAttempts = spec.maxAttempts,
            startedAt = wallClock.now(),
        )
        session.job = scope.launch {
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
                attempt = progress.attempt,
                maxAttempts = session.spec.maxAttempts,
                startedAt = wallClock.now(),
            )
            is ExecutionProgress.RetryScheduled -> OperationState.BackingOff(
                nextAttempt = progress.nextAttempt,
                maxAttempts = session.spec.maxAttempts,
                retryAt = wallClock.now().plusMillis(progress.delay.inWholeMilliseconds),
                reason = progress.reason,
            )
        }
    }

    private fun publishOutcome(token: Long, outcome: ExecutionOutcome<O>) {
        if (current?.token != token) return
        _state.value = when (outcome) {
            is ExecutionOutcome.Success -> OperationState.Succeeded(
                data = outcome.data,
                attemptsUsed = outcome.attemptsUsed,
            )
            is ExecutionOutcome.ManualRetry -> OperationState.Failed(
                failure = outcome.failure,
                recovery = RecoveryAction.Retry,
                attemptsUsed = outcome.attemptsUsed,
            )
            is ExecutionOutcome.Failure -> OperationState.Failed(
                failure = outcome.failure,
                recovery = outcome.recovery,
                attemptsUsed = outcome.attemptsUsed,
            )
        }
    }
}
