package io.github.cleitonmonteiro.requestretry.retry

import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Computes how long to wait before a given (1-based) retry attempt. */
fun interface RetryPolicy {
    fun delayFor(attempt: Int): Duration
}

/**
 * Full jitter: `delay = random(0, min(max, base × factor^(attempt-1)))`. Every attempt's delay is
 * drawn uniformly from zero up to the exponentially-growing cap, which is what actually disperses
 * concurrent clients instead of just wobbling them by a fixed ratio around one value — see
 * `planos/plano-basico-evolucao-retry.md` §10.1. [random] is injected for deterministic tests.
 */
class ExponentialBackoffPolicy(
    private val base: Duration = 1.seconds,
    private val factor: Double = 2.0,
    private val max: Duration = 8.seconds,
    private val random: Random = Random.Default,
) : RetryPolicy {

    init {
        require(base >= Duration.ZERO) { "base must be non-negative" }
        require(factor >= 0.0) { "factor must be non-negative" }
        require(max >= Duration.ZERO) { "max must be non-negative" }
    }

    override fun delayFor(attempt: Int): Duration {
        require(attempt >= 1) { "attempt must be 1-based" }
        val cap = (base * factor.pow(attempt - 1)).coerceIn(Duration.ZERO, max)
        if (cap <= Duration.ZERO) return Duration.ZERO
        return random.nextLong(0, cap.inWholeMilliseconds + 1).milliseconds
    }
}
