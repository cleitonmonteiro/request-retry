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
    private val errorTypeStrategy: ErrorTypeStrategy,
) {
    constructor(retryPolicy: RetryPolicy) : this(retryPolicy, DefaultErrorTypeStrategy)
    fun <T> create(
        scope: CoroutineScope,
        maxRetries: Int = DEFAULT_MAX_ATTEMPTS,
        apiCall: ApiCall<T>,
    ): RetryController<T> =
        RetryController(
            scope = scope,
            apiCall = apiCall,
            retryPolicy = retryPolicy,
            maxAttempts = maxRetries,
            errorTypeStrategy = errorTypeStrategy,
        )

    fun <T> createWithErrorStrategy(
        scope: CoroutineScope,
        maxRetries: Int = DEFAULT_MAX_ATTEMPTS,
        errorTypeStrategy: ErrorTypeStrategy = DefaultErrorTypeStrategy,
        apiCall: ApiCall<T>,
    ): RetryController<T> = RetryController(
        scope = scope,
        apiCall = apiCall,
        retryPolicy = retryPolicy,
        maxAttempts = maxRetries,
        errorTypeStrategy = errorTypeStrategy,
    )

    companion object {
        /** The retry budget every screen gets unless it asks for a different one. */
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_MAX_RETRIES = DEFAULT_MAX_ATTEMPTS
    }
}
