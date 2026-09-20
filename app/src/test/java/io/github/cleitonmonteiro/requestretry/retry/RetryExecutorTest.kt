@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryExecutorTest {
    @Test
    fun `transient failure retries with backoff and succeeds`() = runTest {
        var calls = 0
        val progress = mutableListOf<ExecutionProgress>()
        val executor = RetryExecutor()
        val spec = readSpec(maxAttempts = 3, backoff = FixedBackoff(2.seconds))

        val result = async {
            executor.execute(Unit, spec, OneShotCall { _, _ ->
                calls++
                if (calls < 3) throw RequestFailureException(
                    RequestFailure.Connection(TimeoutStage.CONNECT, OutcomeCertainty.NOT_SENT),
                )
                "done"
            }, progress::add)
        }

        advanceTimeBy(4.seconds)
        assertEquals(ExecutionOutcome.Success("done", 3), result.await())
        assertEquals(3, calls)
        assertTrue(progress.any { it is ExecutionProgress.RetryScheduled })
    }

    @Test
    fun `terminal failure does not spend additional attempts`() = runTest {
        var calls = 0
        val outcome = RetryExecutor().execute(Unit, readSpec(), OneShotCall<Unit, String> { _, _ ->
            calls++
            throw RequestFailureException(RequestFailure.Http(403))
        }) {}

        assertEquals(1, calls)
        assertEquals(
            ExecutionOutcome.Failure(PublicFailure.PermissionDenied, RecoveryAction.Leave, 1),
            outcome,
        )
    }

    @Test
    fun `per-attempt timeout is bounded and classified`() = runTest {
        val spec = readSpec().copy(perAttemptTimeout = 1.seconds)
        val result = async {
            RetryExecutor().execute(Unit, spec, OneShotCall<Unit, String> { _, _ ->
                delay(10.seconds)
                "late"
            }) {}
        }

        advanceTimeBy(30.seconds)
        val outcome = result.await()
        assertTrue(outcome is ExecutionOutcome.Failure)
        assertEquals(3, (outcome as ExecutionOutcome.Failure).attemptsUsed)
    }

    @Test
    fun `cancellation during backoff is transparent`() = runTest {
        val job = async {
            RetryExecutor().execute(Unit, readSpec(backoff = FixedBackoff(5.seconds)), OneShotCall<Unit, String> { _, _ ->
                throw RequestFailureException(
                    RequestFailure.Connection(TimeoutStage.CONNECT, OutcomeCertainty.NOT_SENT),
                )
            }) {}
        }
        advanceTimeBy(1.seconds)
        job.cancelAndJoin()
        assertTrue(job.getCompletionExceptionOrNull() is CancellationException)
    }

    @Test
    fun `no attempt starts after overall deadline`() = runTest {
        var now = 0L
        var calls = 0
        val clock = MonotonicClock { now }
        val executor = RetryExecutor(clock = clock)
        val spec = readSpec(backoff = FixedBackoff(Duration.ZERO))

        val outcome = executor.execute(Unit, spec, OneShotCall<Unit, String> { _, _ ->
            calls++
            now = spec.overallDeadline.inWholeNanoseconds
            throw RequestFailureException(
                RequestFailure.Connection(TimeoutStage.CONNECT, OutcomeCertainty.NOT_SENT),
            )
        }) {}

        assertEquals(1, calls)
        assertEquals(
            ExecutionOutcome.Failure(PublicFailure.DeadlineExceeded, RecoveryAction.Retry, 1),
            outcome,
        )
    }

    private fun readSpec(
        maxAttempts: Int = 3,
        backoff: BackoffStrategy = FixedBackoff(Duration.ZERO),
    ): OperationSpec = OperationProfiles.foregroundRead(
        OperationName.PROFILE_READ,
        OperationId("operation"),
        backoff,
    ).copy(maxAttempts = maxAttempts)
}
