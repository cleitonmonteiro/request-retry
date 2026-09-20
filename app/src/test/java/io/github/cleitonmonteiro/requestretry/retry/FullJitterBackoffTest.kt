package io.github.cleitonmonteiro.requestretry.retry

import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullJitterBackoffTest {
    @Test
    fun `full jitter stays between zero and exponential cap`() {
        repeat(100) { seed ->
            val backoff = FullJitterBackoff(
                baseDelay = 1.seconds,
                factor = 2.0,
                maxDelay = 8.seconds,
                random = Random(seed),
            )
            assertTrue(backoff.delayForRetry(0) in kotlin.time.Duration.ZERO..1.seconds)
            assertTrue(backoff.delayForRetry(1) in kotlin.time.Duration.ZERO..2.seconds)
            assertTrue(backoff.delayForRetry(20) in kotlin.time.Duration.ZERO..8.seconds)
        }
    }

    @Test
    fun `fixed backoff makes virtual time deterministic`() {
        assertEquals(3.seconds, FixedBackoff(3.seconds).delayForRetry(0))
        assertEquals(3.seconds, FixedBackoff(3.seconds).delayForRetry(99))
    }
}
