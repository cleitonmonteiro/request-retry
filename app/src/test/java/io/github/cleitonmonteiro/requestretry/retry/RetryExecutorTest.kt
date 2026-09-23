@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
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
    fun `transient failure returns manual retry immediately with a pending cooldown`() = runTest {
        var calls = 0
        val outcome = RetryExecutor().execute(
            input = Unit,
            spec = readSpec(backoff = FixedBackoff(2.seconds)),
            attempt = 1,
            call = OneShotCall<Unit, String> { _, _ ->
                calls++
                throw RequestFailureException(RequestFailure.Connection)
            },
            onAttemptStarted = {},
        )

        assertEquals(
            ExecutionOutcome.ManualRetry(
                PublicFailure.TemporarilyUnavailable,
                1,
                PendingRetry(2.seconds),
            ),
            outcome,
        )
        assertEquals(1, calls)
    }

    @Test
    fun `supplied pending retry delays the call itself`() = runTest {
        var calls = 0
        val result = async {
            RetryExecutor().execute(
                input = Unit,
                spec = readSpec(),
                attempt = 2,
                pendingRetry = PendingRetry(3.seconds),
                call = OneShotCall<Unit, String> { _, _ ->
                    calls++
                    "ok"
                },
                onAttemptStarted = {},
            )
        }

        runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(3.seconds)

        assertEquals(ExecutionOutcome.Success("ok", 2), result.await())
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
            onAttemptStarted = {},
        )

        assertEquals(
            ExecutionOutcome.Failure(PublicFailure.Unknown, RecoveryAction.Leave, 1),
            outcome,
        )
    }

    @Test
    fun `cancellation during pending retry cooldown is transparent`() = runTest {
        var calls = 0
        val job = async {
            RetryExecutor().execute(
                input = Unit,
                spec = readSpec(),
                attempt = 2,
                pendingRetry = PendingRetry(5.seconds),
                call = OneShotCall<Unit, String> { _, _ -> calls++; "ok" },
                onAttemptStarted = {},
            )
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.getCompletionExceptionOrNull() is CancellationException)
        assertEquals(0, calls)
    }

    @Test
    fun `backoff receives zero-based retry index`() = runTest {
        val indexes = mutableListOf<Int>()
        val backoff = object : BackoffStrategy {
            override val maxDelay = 8.seconds
            override fun delayForRetry(retryIndex: Int): Duration {
                indexes += retryIndex
                return Duration.ZERO
            }
        }

        RetryExecutor().execute(
            input = Unit,
            spec = readSpec(backoff = backoff),
            attempt = 2,
            call = OneShotCall<Unit, String> { _, _ ->
                throw RequestFailureException(RequestFailure.Connection)
            },
            onAttemptStarted = {},
        )

        assertEquals(listOf(1), indexes)
    }

    @Test
    fun `out of range strategy delay is clamped to its bounds`() = runTest {
        val backoff = object : BackoffStrategy {
            override val maxDelay = 8.seconds
            override fun delayForRetry(retryIndex: Int): Duration = (-3).seconds
        }

        val outcome = RetryExecutor().execute(
            input = Unit,
            spec = readSpec(backoff = backoff),
            attempt = 1,
            call = OneShotCall<Unit, String> { _, _ ->
                throw RequestFailureException(RequestFailure.Connection)
            },
            onAttemptStarted = {},
        )

        assertEquals(
            Duration.ZERO,
            (outcome as ExecutionOutcome.ManualRetry).pendingRetry.delay,
        )
    }

    private fun readSpec(
        backoff: BackoffStrategy = FixedBackoff(Duration.ZERO),
    ): OperationSpec = OperationProfiles.foreground(
        OperationName("profile_read"),
        backoff,
    )
}
