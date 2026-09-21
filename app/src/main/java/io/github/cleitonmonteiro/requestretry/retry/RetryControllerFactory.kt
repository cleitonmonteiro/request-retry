package io.github.cleitonmonteiro.requestretry.retry

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Builds [RetryController]s sharing the app's one injected [RetryPolicy], [RetryDecider] and
 * [RetryObserver], so backoff tuning and retry-decision policy each have a single home
 * (`di/RetryModule.kt`) instead of being constructor defaults repeated at every call site. This
 * is the only `retry/` type that carries a DI annotation — `@Inject` is bare JSR-330, no Dagger
 * types — so the package otherwise stays framework-free.
 */
class RetryControllerFactory @Inject constructor(
    private val retryPolicy: RetryPolicy,
    private val decider: RetryDecider,
    private val observer: RetryObserver,
) {
    constructor(retryPolicy: RetryPolicy) : this(retryPolicy, DefaultRetryDecider, RetryObserver.NoOp)

    fun <T> create(
        scope: CoroutineScope,
        spec: OperationSpec,
        apiCall: ApiCall<T>,
    ): RetryController<T> = RetryController(
        scope = scope,
        apiCall = apiCall,
        spec = spec,
        retryPolicy = retryPolicy,
        decider = decider,
        observer = observer,
    )
}
