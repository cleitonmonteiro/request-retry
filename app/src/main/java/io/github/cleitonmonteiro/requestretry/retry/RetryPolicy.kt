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

    override fun delayFor(attempt: Int): Duration {
        val raw = base * factor.pow(attempt - 1)
        val capped = raw.coerceAtMost(max)
        val jitter = if (jitterRatio > 0.0) 1.0 + random.nextDouble(-jitterRatio, jitterRatio) else 1.0
        return (capped * jitter).coerceAtLeast(Duration.ZERO)
    }
}
