package io.github.cleitonmonteiro.requestretry.retry

import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * Builds [RetryController]s sharing one app-wide [RetryPolicy], so backoff tuning has a single
 * home instead of being a constructor default repeated at each call site. This is the only
 * `retry/` type that carries a DI annotation — `@Inject` is bare JSR-330, no Dagger types — so
 * the package otherwise stays framework-free.
 */
class RetryControllerFactory @Inject constructor(
    private val retryPolicy: RetryPolicy,
) {
    fun <T> create(scope: CoroutineScope, apiCall: ApiCall<T>): RetryController<T> =
        RetryController(scope = scope, apiCall = apiCall, retryPolicy = retryPolicy)
}
