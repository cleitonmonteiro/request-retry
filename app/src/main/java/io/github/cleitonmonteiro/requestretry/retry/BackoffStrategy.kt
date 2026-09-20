package io.github.cleitonmonteiro.requestretry.retry

import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

interface BackoffStrategy {
    val maxDelay: Duration
    fun delayForRetry(retryIndex: Int): Duration
}

/** Full-jitter exponential backoff: a uniform delay between zero and the capped delay. */
class FullJitterBackoff(
    private val baseDelay: Duration = 1.seconds,
    private val factor: Double = 2.0,
    override val maxDelay: Duration = 8.seconds,
    private val random: Random = Random.Default,
) : BackoffStrategy {
    init {
        require(baseDelay.isFinite() && !baseDelay.isNegative()) { "baseDelay must be finite and non-negative" }
        require(factor >= 1.0 && factor.isFinite()) { "factor must be finite and at least 1" }
        require(maxDelay.isFinite() && !maxDelay.isNegative()) { "maxDelay must be finite and non-negative" }
    }

    override fun delayForRetry(retryIndex: Int): Duration {
        require(retryIndex >= 0) { "retryIndex must be zero-based" }
        val exponentialNanos = baseDelay.inWholeNanoseconds.toDouble() * factor.pow(retryIndex)
        val capNanos = exponentialNanos
            .coerceAtMost(maxDelay.inWholeNanoseconds.toDouble())
            .coerceAtMost(Long.MAX_VALUE.toDouble())
            .toLong()
        if (capNanos <= 0L) return Duration.ZERO
        val randomNanos = if (capNanos == Long.MAX_VALUE) {
            random.nextLong().ushr(1)
        } else {
            random.nextLong(capNanos + 1)
        }
        return randomNanos.nanoseconds
    }
}

class FixedBackoff(override val maxDelay: Duration) : BackoffStrategy {
    init {
        require(maxDelay.isFinite() && !maxDelay.isNegative())
    }

    override fun delayForRetry(retryIndex: Int): Duration {
        require(retryIndex >= 0)
        return maxDelay
    }
}
