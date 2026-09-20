package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.TimeoutStage
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResilienceGuardsTest {
    @Test
    fun `retry budget bounds amplification per operation name`() {
        val budget = SlidingWindowRetryBudget(maxRetryRatio = 1.0, minimumRetryAllowance = 1)
        budget.onOriginalRequest(OperationName.PROFILE_READ)

        assertTrue(budget.tryAcquireRetry(OperationName.PROFILE_READ))
        assertFalse(budget.tryAcquireRetry(OperationName.PROFILE_READ))
        assertTrue(budget.tryAcquireRetry(OperationName.ORDERS_READ))
    }

    @Test
    fun `circuit breaker opens and permits one half-open probe`() {
        val clock = MutableClock()
        val breaker = ThresholdCircuitBreaker(failureThreshold = 2, openDuration = 10.seconds, clock = clock)
        val failure = RequestFailure.Connection(TimeoutStage.CONNECT, OutcomeCertainty.NOT_SENT)

        breaker.onFailure(OperationName.PROFILE_READ, failure)
        breaker.onFailure(OperationName.PROFILE_READ, failure)
        assertFalse(breaker.allow(OperationName.PROFILE_READ))

        clock.nanos += 10.seconds.inWholeNanoseconds
        assertTrue(breaker.allow(OperationName.PROFILE_READ))
        assertFalse(breaker.allow(OperationName.PROFILE_READ))
        breaker.onSuccess(OperationName.PROFILE_READ)
        assertTrue(breaker.allow(OperationName.PROFILE_READ))
    }

    private class MutableClock(var nanos: Long = 0L) : MonotonicClock {
        override fun nowNanos(): Long = nanos
    }
}
