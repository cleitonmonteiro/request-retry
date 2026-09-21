@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryExecutorTest {

    private val executor = RetryExecutor()

    @Test
    fun `a successful call returns its value`() = runTest {
        val result = executor.attempt(ApiCall { flowOf("payload") }, perAttemptTimeout = 5.seconds)
        assertEquals(AttemptResult.Success("payload"), result)
    }

    @Test
    fun `a call that outlasts the per-attempt timeout becomes a RESPONSE timeout failure`() = runTest {
        val result = executor.attempt(ApiCall<String> { flow { delay(1.hours) } }, perAttemptTimeout = 5.seconds)

        val failure = (result as AttemptResult.Failure).failure as RequestFailure.Timeout
        assertEquals(TimeoutStage.RESPONSE, failure.stage)
        assertEquals(OutcomeCertainty.MAY_HAVE_REACHED_SERVER, failure.certainty)
    }

    @Test
    fun `a RequestFailureException is classified back to its RequestFailure`() = runTest {
        val failure = RequestFailure.Http(500)
        val result = executor.attempt(
            ApiCall<String> { flow { throw RequestFailureException(failure) } },
            perAttemptTimeout = 5.seconds,
        )

        assertEquals(AttemptResult.Failure(failure), result)
    }

    @Test
    fun `a superseded job's cancellation is propagated, not classified as a failure`() = runTest {
        var classified = false
        val classifier = FailureClassifier { classified = true; RequestFailure.Unknown("should not run") }
        val executorWithClassifier = RetryExecutor(classifier)

        val job = launch {
            executorWithClassifier.attempt(ApiCall<String> { flow { delay(1.hours) } }, perAttemptTimeout = 5.hours)
        }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(false, classified)
    }
}
