package io.github.cleitonmonteiro.requestretry.retry

import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Computes how long to wait before a given (1-based) retry attempt. */
fun interface RetryPolicy {
    fun delayFor(attempt: Int): Duration
}

/**
 * Doubles the delay on every attempt, capped at [max], with up to [jitterRatio] of
 * random noise applied so concurrent clients don't retry in lockstep.
 */
class ExponentialBackoffPolicy(
    private val base: Duration = 1.seconds,
    private val factor: Double = 2.0,
    private val max: Duration = 8.seconds,
    private val jitterRatio: Double = 0.2,
    private val random: Random = Random.Default,
) : RetryPolicy {

    init {
        require(base >= Duration.ZERO) { "base must be non-negative" }
        require(factor >= 0.0) { "factor must be non-negative" }
        require(max >= Duration.ZERO) { "max must be non-negative" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be between 0 and 1" }
    }

    override fun delayFor(attempt: Int): Duration {
        require(attempt >= 1) { "attempt must be 1-based" }
        val raw = (base * factor.pow(attempt - 1)).coerceAtMost(max)
        val jitter = if (jitterRatio > 0.0) {
            1.0 + random.nextDouble(-jitterRatio, 0.0)
        } else 1.0
        // Cap before applying downward jitter so high-attempt retries still vary while never
        // exceeding max.
        return (raw * jitter).coerceIn(Duration.ZERO, max)
    }
}
