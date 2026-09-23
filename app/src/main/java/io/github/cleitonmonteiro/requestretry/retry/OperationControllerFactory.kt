package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.data.observability.DebugLogger
import io.github.cleitonmonteiro.requestretry.data.observability.NoOpDebugLogger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/** Creates controllers that share the injected retry executor and debug logger. */
class OperationControllerFactory @Inject constructor(
    private val executor: RetryExecutor,
    private val logger: DebugLogger,
) {
    /** Builds its own executor without telemetry or logging, for tests that skip the Hilt graph. */
    constructor(
        failureClassifier: FailureClassifier = DefaultFailureClassifier,
        retryDecider: RetryDecider = ConservativeRetryDecider,
    ) : this(
        RetryExecutor(failureClassifier, retryDecider, NoOpRetryObserver),
        NoOpDebugLogger,
    )

    /** Returns an independent controller whose mailbox and executions run in [scope]. */
    fun <I, O> create(
        scope: CoroutineScope,
        specFactory: OperationSpecFactory<I>,
        call: OneShotCall<I, O>,
    ): OperationController<I, O> = OperationController(
        scope = scope,
        specFactory = specFactory,
        executor = executor,
        call = call,
        logger = logger,
    )
}
