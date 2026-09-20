package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import java.util.ArrayDeque
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun interface MonotonicClock {
    fun nowNanos(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowNanos(): Long = System.nanoTime()
}

interface RetryBudget {
    fun onOriginalRequest(operationName: OperationName)
    fun tryAcquireRetry(operationName: OperationName): Boolean
}

object UnlimitedRetryBudget : RetryBudget {
    override fun onOriginalRequest(operationName: OperationName) = Unit
    override fun tryAcquireRetry(operationName: OperationName): Boolean = true
}

/** A bounded, per-operation-name retry ratio over a monotonic sliding window. */
class SlidingWindowRetryBudget(
    private val maxRetryRatio: Double = 0.25,
    private val minimumRetryAllowance: Int = 2,
    private val window: Duration = 60.seconds,
    private val clock: MonotonicClock = SystemMonotonicClock,
) : RetryBudget {
    init {
        require(maxRetryRatio in 0.0..1.0)
        require(minimumRetryAllowance >= 0)
        require(window.isPositive() && window.isFinite())
    }

    private data class Counters(
        val originals: ArrayDeque<Long> = ArrayDeque(),
        val retries: ArrayDeque<Long> = ArrayDeque(),
    )

    private val counters = mutableMapOf<OperationName, Counters>()

    @Synchronized
    override fun onOriginalRequest(operationName: OperationName) {
        val now = clock.nowNanos()
        val value = counters.getOrPut(operationName, ::Counters)
        prune(value, now)
        value.originals.addLast(now)
    }

    @Synchronized
    override fun tryAcquireRetry(operationName: OperationName): Boolean {
        val now = clock.nowNanos()
        val value = counters.getOrPut(operationName, ::Counters)
        prune(value, now)
        val allowance = maxOf(minimumRetryAllowance, (value.originals.size * maxRetryRatio).toInt())
        if (value.retries.size >= allowance) return false
        value.retries.addLast(now)
        return true
    }

    private fun prune(counters: Counters, now: Long) {
        val oldest = now - window.inWholeNanoseconds
        while (counters.originals.firstOrNull()?.let { it < oldest } == true) counters.originals.removeFirst()
        while (counters.retries.firstOrNull()?.let { it < oldest } == true) counters.retries.removeFirst()
    }
}

interface CircuitBreaker {
    fun allow(operationName: OperationName): Boolean
    fun onSuccess(operationName: OperationName)
    fun onFailure(operationName: OperationName, failure: RequestFailure)
}

object NoOpCircuitBreaker : CircuitBreaker {
    override fun allow(operationName: OperationName): Boolean = true
    override fun onSuccess(operationName: OperationName) = Unit
    override fun onFailure(operationName: OperationName, failure: RequestFailure) = Unit
}

class ThresholdCircuitBreaker(
    private val failureThreshold: Int = 5,
    private val openDuration: Duration = 30.seconds,
    private val clock: MonotonicClock = SystemMonotonicClock,
) : CircuitBreaker {
    init {
        require(failureThreshold > 0)
        require(openDuration.isPositive() && openDuration.isFinite())
    }

    private data class State(var failures: Int = 0, var openUntilNanos: Long = 0L, var halfOpenProbe: Boolean = false)
    private val states = mutableMapOf<OperationName, State>()

    @Synchronized
    override fun allow(operationName: OperationName): Boolean {
        val state = states.getOrPut(operationName, ::State)
        val now = clock.nowNanos()
        if (state.openUntilNanos == 0L) return true
        if (now < state.openUntilNanos) return false
        if (state.halfOpenProbe) return false
        state.halfOpenProbe = true
        return true
    }

    @Synchronized
    override fun onSuccess(operationName: OperationName) {
        states[operationName] = State()
    }

    @Synchronized
    override fun onFailure(operationName: OperationName, failure: RequestFailure) {
        if (!failure.countsForCircuitBreaker()) return
        val state = states.getOrPut(operationName, ::State)
        state.halfOpenProbe = false
        state.failures++
        if (state.failures >= failureThreshold) {
            state.openUntilNanos = clock.nowNanos() + openDuration.inWholeNanoseconds
        }
    }
}

private fun RequestFailure.countsForCircuitBreaker(): Boolean = when (this) {
    is RequestFailure.Connection,
    is RequestFailure.Dns,
    is RequestFailure.Timeout,
    is RequestFailure.RateLimited,
    -> true
    is RequestFailure.Http -> statusCode >= 500
    else -> false
}

class Bulkhead(maxConcurrentRequests: Int = 8) {
    private val semaphore = Semaphore(maxConcurrentRequests)
    suspend fun <T> execute(block: suspend () -> T): T = semaphore.withPermit { block() }
}
