@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        )

        assertEquals(
            ExecutionOutcome.ManualRetry(PublicFailure.TemporarilyUnavailable, 1, 2.seconds),
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
                pendingRetry = 3.seconds,
                call = OneShotCall<Unit, String> { _, _ ->
                    calls++
                    "ok"
                },
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
                pendingRetry = 5.seconds,
                call = OneShotCall<Unit, String> { _, _ -> calls++; "ok" },
            )
        }
        runCurrent()
        job.cancelAndJoin()
        assertTrue(job.getCompletionExceptionOrNull() is CancellationException)
        assertEquals(0, calls)
    }

    @Test
    fun `cancelling the attempt during the call propagates without an outcome`() = runTest {
        var outcome: ExecutionOutcome<String>? = null
        val job = launch {
            outcome = RetryExecutor().execute(
                input = Unit,
                spec = readSpec(),
                attempt = 1,
                call = OneShotCall<Unit, String> { _, _ -> awaitCancellation() },
            )
        }
        runCurrent()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertNull(outcome)
    }

    @Test
    fun `cancellation not caused by cancelling the attempt fails closed`() = runTest {
        val outcome = RetryExecutor().execute(
            input = Unit,
            spec = readSpec(),
            attempt = 1,
            call = OneShotCall<Unit, String> { _, _ -> withTimeout(1.seconds) { awaitCancellation() } },
        )

        assertEquals(
            ExecutionOutcome.Failure(PublicFailure.Unknown, RecoveryAction.Leave, 1),
            outcome,
        )
    }

    @Test
    fun `throwing observer never turns a success into a failure`() = runTest {
        val observer = ThrowingObserver()

        val outcome = RetryExecutor(observer = observer).execute(
            input = Unit,
            spec = readSpec(),
            attempt = 2,
            pendingRetry = 1.seconds,
            call = OneShotCall<Unit, String> { _, _ -> "ok" },
        )

        assertEquals(ExecutionOutcome.Success("ok", 2), outcome)
        assertEquals(5, observer.calls)
    }

    @Test
    fun `throwing observer never escapes a failed attempt`() = runTest {
        val observer = ThrowingObserver()

        val outcome = RetryExecutor(observer = observer).execute(
            input = Unit,
            spec = readSpec(backoff = FixedBackoff(2.seconds)),
            attempt = 1,
            call = OneShotCall<Unit, String> { _, _ ->
                throw RequestFailureException(RequestFailure.Connection)
            },
        )

        assertEquals(
            ExecutionOutcome.ManualRetry(PublicFailure.TemporarilyUnavailable, 1, 2.seconds),
            outcome,
        )
        assertEquals(5, observer.calls)
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
        )

        assertEquals(
            Duration.ZERO,
            (outcome as ExecutionOutcome.ManualRetry).pendingRetry,
        )
    }

    private fun readSpec(
        backoff: BackoffStrategy = FixedBackoff(Duration.ZERO),
    ): OperationSpec = OperationSpec(OperationName("profile_read"), backoff = backoff)

    /** Fails every callback, counting them to prove each one was reached and survived. */
    private class ThrowingObserver : RetryObserver {
        var calls = 0

        override fun onOperationStarted(context: OperationTelemetryContext) = throwTelemetryError()
        override fun onAttemptStarted(context: AttemptTelemetryContext) = throwTelemetryError()
        override fun onAttemptFinished(result: AttemptTelemetryResult) = throwTelemetryError()
        override fun onRetryScheduled(event: RetryScheduledEvent) = throwTelemetryError()
        override fun onBackoffDelayStarted(context: BackoffDelayContext) = throwTelemetryError()
        override fun onOperationFinished(result: OperationTelemetryResult) = throwTelemetryError()

        private fun throwTelemetryError(): Nothing {
            calls++
            throw IllegalStateException("telemetry sink unavailable")
        }
    }
}
