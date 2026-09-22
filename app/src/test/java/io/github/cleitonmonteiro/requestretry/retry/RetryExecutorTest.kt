@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryExecutorTest {
    @Test
    fun `transient failure waits then makes manual retry available without another call`() = runTest {
        var calls = 0
        val progress = mutableListOf<ExecutionProgress>()
        val result = async {
            RetryExecutor().execute(
                input = Unit,
                spec = readSpec(backoff = FixedBackoff(2.seconds)),
                attempt = 1,
                call = OneShotCall<Unit, String> { _, _ ->
                    calls++
                    throw RequestFailureException(
                        RequestFailure.Connection(mayHaveReachedServer = false),
                    )
                },
                onProgress = progress::add,
            )
        }

        runCurrent()
        assertEquals(1, calls)
        assertTrue(progress.last() is ExecutionProgress.RetryScheduled)
        advanceTimeBy(2.seconds)

        assertEquals(
            ExecutionOutcome.ManualRetry(PublicFailure.TemporarilyUnavailable, 1),
            result.await(),
        )
        assertEquals(1, calls)
    }

    @Test
    fun `terminal failure does not make retry available`() = runTest {
        val outcome = RetryExecutor().execute(
            input = Unit,
            spec = readSpec(),
            attempt = 1,
            call = OneShotCall<Unit, String> { _, _ ->
                throw RequestFailureException(RequestFailure.Http(403))
            },
            onProgress = {},
        )

        assertEquals(
            ExecutionOutcome.Failure(PublicFailure.Unknown, RecoveryAction.Leave, 1),
            outcome,
        )
    }

    @Test
    fun `cancellation during cooldown is transparent`() = runTest {
        val job = async {
            RetryExecutor().execute(
                input = Unit,
                spec = readSpec(backoff = FixedBackoff(5.seconds)),
                attempt = 1,
                call = OneShotCall<Unit, String> { _, _ ->
                    throw RequestFailureException(
                        RequestFailure.Connection(mayHaveReachedServer = false),
                    )
                },
                onProgress = {},
            )
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.getCompletionExceptionOrNull() is CancellationException)
    }

    private fun readSpec(
        backoff: BackoffStrategy = FixedBackoff(Duration.ZERO),
    ): OperationSpec = OperationProfiles.foregroundRead(
        OperationName("profile_read"),
        OperationId("operation"),
        backoff,
    )
}
