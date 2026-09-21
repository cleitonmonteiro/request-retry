package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.withTimeout

/** The result of one attempt, before any retry decision has been made about it. */
sealed interface AttemptResult<out T> {
    data class Success<T>(val value: T) : AttemptResult<T>
    data class Failure(val failure: RequestFailure) : AttemptResult<Nothing>
}

/**
 * Executes exactly one attempt of a single-shot [ApiCall] under [perAttemptTimeout], classifying
 * any failure into a [RequestFailure]. Pure: no Android, Compose or Hilt imports, no retry loop
 * and no mutable state — [RetryController] owns the loop and the [RetryUiState] it publishes.
 * See `planos/plano-basico-evolucao-retry.md` §5.1.
 */
class RetryExecutor(
    private val classifier: FailureClassifier = DefaultFailureClassifier,
) {
    suspend fun <T> attempt(apiCall: ApiCall<T>, perAttemptTimeout: Duration): AttemptResult<T> = try {
        AttemptResult.Success(withTimeout(perAttemptTimeout) { apiCall().single() })
    } catch (timeout: TimeoutCancellationException) {
        // withTimeout signals via a CancellationException subtype, so it must be caught ahead of
        // the plain CancellationException branch below — this is a real, classified failure, not
        // a superseded job being cancelled by the controller.
        AttemptResult.Failure(
            RequestFailure.Timeout(stage = TimeoutStage.RESPONSE, certainty = OutcomeCertainty.MAY_HAVE_REACHED_SERVER),
        )
    } catch (cancellation: CancellationException) {
        // A genuinely cancelled job (superseded by a newer load()/retry(), or the scope itself
        // ending) is never classified as a failure — mirrors the original RetryController's
        // Flow.catch transparency, now enforced at the executor boundary instead.
        throw cancellation
    } catch (error: Throwable) {
        if (error is Error) throw error
        AttemptResult.Failure(classifier.classify(error))
    }
}
