package io.github.cleitonmonteiro.requestretry.data.remote

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeNetworkTest {

    @Test
    fun `execute succeeds immediately under ALWAYS_SUCCEED`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val network = FakeNetwork(scenarios)

        // Act
        val result = network.execute { "payload" }

        // Assert
        assertEquals("payload", result)
    }

    @Test
    fun `execute always fails under ALWAYS_FAIL`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_FAIL) }
        val network = FakeNetwork(scenarios)

        // Act
        val thrown = runCatching { network.execute { "payload" } }.exceptionOrNull()

        // Assert
        assertTrue(thrown is IOException)
    }

    @Test
    fun `execute succeeds only from the third call under SUCCEED_ON_THIRD_ATTEMPT`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.SUCCEED_ON_THIRD_ATTEMPT) }
        val network = FakeNetwork(scenarios)

        // Act
        val firstAttempt = runCatching { network.execute { "payload" } }.exceptionOrNull()
        val secondAttempt = runCatching { network.execute { "payload" } }.exceptionOrNull()
        val thirdAttempt = network.execute { "payload" }

        // Assert
        assertTrue(firstAttempt is IOException)
        assertTrue(secondAttempt is IOException)
        assertEquals("payload", thirdAttempt)
    }

    @Test
    fun `re-selecting the same scenario restarts the attempt count`() = runTest {
        // Arrange: burn two attempts of SUCCEED_ON_THIRD_ATTEMPT, one short of success
        val scenarios = ScenarioHolder().apply { select(Scenario.SUCCEED_ON_THIRD_ATTEMPT) }
        val network = FakeNetwork(scenarios)
        runCatching { network.execute { "payload" } }
        runCatching { network.execute { "payload" } }

        // Act: re-tapping the same scenario chip should start the demo over
        scenarios.select(Scenario.SUCCEED_ON_THIRD_ATTEMPT)
        val afterReselect = runCatching { network.execute { "payload" } }.exceptionOrNull()

        // Assert
        assertTrue(afterReselect is IOException)
    }
}
