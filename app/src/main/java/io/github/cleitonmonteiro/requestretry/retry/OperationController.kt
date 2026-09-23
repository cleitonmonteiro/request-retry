package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.data.observability.DebugLogger
import io.github.cleitonmonteiro.requestretry.data.observability.NoOpDebugLogger
import kotlin.time.Duration
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

/**
 * Serializes all public commands and execution events through one mailbox. The input stored in a
 * session is immutable, so retries and status verification can never observe later UI edits.
 */
class OperationController<I, O>(
    private val scope: CoroutineScope,
    private val specFactory: OperationSpecFactory<I>,
    private val executor: RetryExecutor,
    private val call: OneShotCall<I, O>,
    private val logger: DebugLogger = NoOpDebugLogger,
) {
    /** Immutable command snapshot that prevents retries from observing later form edits. */
    private class Session<I>(
        val input: I,
        val spec: OperationSpec,
    ) {
        lateinit var job: Job

        /** Cooldown decided by the failure that ended this session, spent by the next retry it authorizes. */
        var pendingRetry: Duration? = null
    }

    private val _state = MutableStateFlow<OperationState<O>>(OperationState.Idle)
    val state: StateFlow<OperationState<O>> = _state.asStateFlow()

    /** Generous headroom over any realistic burst of manual taps; a full mailbox signals a caller bug. */
    private val mailbox = Channel<suspend () -> Unit>(capacity = 64)
    private var current: Session<I>? = null

    init {
        scope.launch {
            for (command in mailbox) command()
        }
    }

    /** Cancels any execution in flight and starts a fresh session from this input snapshot. */
    fun start(input: I) {
        enqueue {
            current?.job?.takeIf { it.isActive }?.cancel()
            begin(input, specFactory.create(input), attempt = 1)
        }
    }

    /** No-ops unless the current session is terminally [OperationState.Failed] with [RecoveryAction.Retry]. */
    fun retry() {
        enqueue {
            if (current?.job?.isActive == true) return@enqueue
            val failed = _state.value as? OperationState.Failed ?: return@enqueue
            if (failed.recovery != RecoveryAction.Retry) return@enqueue
            val session = current ?: return@enqueue
            begin(session.input, session.spec, failed.attemptsUsed + 1, session.pendingRetry)
        }
    }

    private fun enqueue(command: suspend () -> Unit) {
        if (mailbox.trySend(command).isFailure) {
            logger.d("OperationController", "mailbox full, dropping command")
        }
    }

    private fun begin(input: I, spec: OperationSpec, attempt: Int, pendingRetry: Duration? = null) {
        val session = Session(input, spec)
        current = session
        _state.value = OperationState.Running(attempt = attempt, maxAttempts = spec.maxAttempts)
        session.job = scope.launch {
            val outcome = executor.execute(input, spec, attempt, call, pendingRetry)
            mailbox.send { publishOutcome(session, outcome) }
        }
    }

    private fun publishOutcome(session: Session<I>, outcome: ExecutionOutcome<O>) {
        if (current !== session) return
        _state.value = when (outcome) {
            is ExecutionOutcome.Success -> OperationState.Succeeded(
                data = outcome.data,
                attemptsUsed = outcome.attemptsUsed,
            )
            is ExecutionOutcome.ManualRetry -> {
                session.pendingRetry = outcome.pendingRetry
                OperationState.Failed(
                    failure = outcome.failure,
                    recovery = RecoveryAction.Retry,
                    attemptsUsed = outcome.attemptsUsed,
                )
            }
            is ExecutionOutcome.Failure -> OperationState.Failed(
                failure = outcome.failure,
                recovery = outcome.recovery,
                attemptsUsed = outcome.attemptsUsed,
            )
        }
    }
}
