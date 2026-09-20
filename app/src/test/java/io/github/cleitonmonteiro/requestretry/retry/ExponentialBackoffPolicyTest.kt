package io.github.cleitonmonteiro.requestretry.retry

import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExponentialBackoffPolicyTest {

    @Test
    fun `delayFor doubles the delay on each attempt when jitter is disabled`() {
        // Arrange
        val policy = ExponentialBackoffPolicy(
            base = 1.seconds,
            factor = 2.0,
            max = 8.seconds,
            jitterRatio = 0.0,
        )

        // Act
        val delays = listOf(1, 2, 3).map { attempt -> policy.delayFor(attempt) }

        // Assert
        assertEquals(listOf(1.seconds, 2.seconds, 4.seconds), delays)
    }

    @Test
    fun `delayFor caps the delay at max when jitter is disabled`() {
        // Arrange
        val policy = ExponentialBackoffPolicy(
            base = 1.seconds,
            factor = 2.0,
            max = 8.seconds,
            jitterRatio = 0.0,
        )

        // Act
        val delay = policy.delayFor(attempt = 10)

        // Assert
        assertEquals(8.seconds, delay)
    }

    @Test
    fun `delayFor never exceeds max, even after jitter is applied to an already-capped delay`() {
        // A high attempt number's raw exponential delay is far past max; jittering it after
        // capping (the bug) could push the result up to jitterRatio past max instead of at it.
        repeat(200) { seed ->
            val policy = ExponentialBackoffPolicy(
                base = 1.seconds,
                factor = 2.0,
                max = 8.seconds,
                jitterRatio = 0.2,
                random = Random(seed),
            )

            val delay = policy.delayFor(attempt = 10)

            assertTrue("delay $delay exceeded max 8s for seed $seed", delay <= 8.seconds)
        }
    }

    @Test
    fun `delayFor stays within the jitter bounds and never goes negative`() {
        // Arrange
        val policy = ExponentialBackoffPolicy(
            base = 1.seconds,
            factor = 2.0,
            max = 8.seconds,
            jitterRatio = 0.2,
            random = Random(seed = 42),
        )
        val uncappedDelayForAttempt2 = 2.seconds

        // Act
        val delay = policy.delayFor(attempt = 2)

        // Assert
        assertTrue(delay >= uncappedDelayForAttempt2 * 0.8)
        assertTrue(delay <= uncappedDelayForAttempt2 * 1.2)
    }
}
