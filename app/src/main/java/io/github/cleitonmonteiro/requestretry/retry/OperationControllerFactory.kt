package io.github.cleitonmonteiro.requestretry.retry

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class OperationControllerFactory @Inject constructor(
    private val failureClassifier: FailureClassifier,
    private val retryDecider: RetryDecider,
    private val observer: RetryObserver,
) {
    constructor(
        failureClassifier: FailureClassifier = DefaultFailureClassifier,
        retryDecider: RetryDecider = ConservativeRetryDecider,
    ) : this(
        failureClassifier,
        retryDecider,
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
            observer = observer,
        ),
        call = call,
        verifier = verifier,
    )
}
