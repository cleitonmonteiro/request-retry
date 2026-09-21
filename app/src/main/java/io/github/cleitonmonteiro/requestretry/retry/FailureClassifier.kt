package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import kotlinx.serialization.SerializationException

/**
 * Turns whatever a data source threw into a [RequestFailure]. [RetryExecutor] calls this on
 * every non-[kotlinx.coroutines.CancellationException] — cancellation is never classified, see
 * `RetryControllerTest`'s cancellation-transparency test.
 */
fun interface FailureClassifier {
    fun classify(error: Throwable): RequestFailure
}

/**
 * The default classifier. [io.github.cleitonmonteiro.requestretry.data.remote.ApiClient] already
 * does the transport → [RequestFailure] mapping and throws a [RequestFailureException], so the
 * common case here is just unwrapping it. Anything else — a mapper bug, a use case throwing a
 * plain exception — becomes [RequestFailure.Unknown], which [DefaultRetryDecider] always treats
 * as terminal: fail closed by default, per principle 1 in the plan.
 */
object DefaultFailureClassifier : FailureClassifier {
    override fun classify(error: Throwable): RequestFailure = when (error) {
        is RequestFailureException -> error.failure
        is SerializationException -> RequestFailure.Protocol(error.message ?: "serialization")
        else -> RequestFailure.Unknown(error::class.simpleName ?: "unknown")
    }
}
