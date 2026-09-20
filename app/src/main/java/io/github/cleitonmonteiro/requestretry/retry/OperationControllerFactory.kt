package io.github.cleitonmonteiro.requestretry.retry

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class OperationControllerFactory @Inject constructor(
    private val failureClassifier: FailureClassifier,
    private val retryDecider: RetryDecider,
    private val retryBudget: RetryBudget,
    private val circuitBreaker: CircuitBreaker,
    private val bulkhead: Bulkhead,
    private val observer: RetryObserver,
) {
    constructor(
        failureClassifier: FailureClassifier = DefaultFailureClassifier,
        retryDecider: RetryDecider = ConservativeRetryDecider,
    ) : this(
        failureClassifier,
        retryDecider,
        UnlimitedRetryBudget,
        NoOpCircuitBreaker,
        Bulkhead(),
        NoOpRetryObserver,
    )

    fun <I, O> create(
        scope: CoroutineScope,
        specFactory: OperationSpecFactory<I>,
        verifier: StatusVerifier<I, O>? = null,
        call: OneShotCall<I, O>,
    ): OperationController<I, O> = OperationController(
        scope = scope,
        specFactory = specFactory,
        executor = RetryExecutor(
            failureClassifier = failureClassifier,
            retryDecider = retryDecider,
            retryBudget = retryBudget,
            circuitBreaker = circuitBreaker,
            bulkhead = bulkhead,
            observer = observer,
        ),
        call = call,
        verifier = verifier,
    )
}
