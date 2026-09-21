package io.github.cleitonmonteiro.requestretry.retry

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExponentialBackoffPolicyTest {

    @Test
    fun `delayFor is always in range 0 to the exponentially growing cap`() {
        // Arrange: full jitter — delay = random(0, cap) — so every draw must land in that range
        repeat(200) { seed ->
            val policy = ExponentialBackoffPolicy(
                base = 1.seconds,
                factor = 2.0,
                max = 8.seconds,
                random = Random(seed),
            )
            val caps = listOf(1.seconds, 2.seconds, 4.seconds, 8.seconds, 8.seconds) // attempts 1..5

            caps.forEachIndexed { index, cap ->
                val delay = policy.delayFor(attempt = index + 1)
                assertTrue("delay $delay out of [0, $cap] for attempt ${index + 1}, seed $seed", delay in Duration.ZERO..cap)
            }
        }
    }

    @Test
    fun `delayFor never exceeds max, even at a high attempt number`() {
        repeat(200) { seed ->
            val policy = ExponentialBackoffPolicy(
                base = 1.seconds,
                factor = 2.0,
                max = 8.seconds,
                random = Random(seed),
            )

            val delay = policy.delayFor(attempt = 10)

            assertTrue("delay $delay exceeded max 8s for seed $seed", delay <= 8.seconds)
            assertTrue("delay $delay was negative for seed $seed", delay >= Duration.ZERO)
        }
    }

    @Test
    fun `a Random that always returns the top of its range yields the full cap`() {
        // Arrange: a Random stubbed to always pick the upper bound isolates the cap computation
        // (exponential growth + saturation at max) from the jitter itself.
        val alwaysMax = object : Random() {
            override fun nextBits(bitCount: Int): Int = error("not used")
            override fun nextLong(from: Long, until: Long): Long = until - 1
        }
        val policy = ExponentialBackoffPolicy(base = 1.seconds, factor = 2.0, max = 8.seconds, random = alwaysMax)

        assertEquals(1.seconds, policy.delayFor(attempt = 1))
        assertEquals(2.seconds, policy.delayFor(attempt = 2))
        assertEquals(4.seconds, policy.delayFor(attempt = 3))
        assertEquals(8.seconds, policy.delayFor(attempt = 4))
        assertEquals(8.seconds, policy.delayFor(attempt = 10))
    }

    @Test
    fun `a Random that always returns zero yields no delay at all`() {
        val alwaysZero = object : Random() {
            override fun nextBits(bitCount: Int): Int = error("not used")
            override fun nextLong(from: Long, until: Long): Long = from
        }
        val policy = ExponentialBackoffPolicy(base = 1.seconds, factor = 2.0, max = 8.seconds, random = alwaysZero)

        assertEquals(Duration.ZERO, policy.delayFor(attempt = 1))
        assertEquals(Duration.ZERO, policy.delayFor(attempt = 5))
    }
}
