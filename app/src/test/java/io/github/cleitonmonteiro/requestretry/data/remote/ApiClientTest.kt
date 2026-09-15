package io.github.cleitonmonteiro.requestretry.data.remote

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {

    @Test
    fun `execute succeeds immediately under ALWAYS_SUCCEED`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val client = ApiClient(scenarios)

        // Act
        val result = client.execute { "payload" }

        // Assert
        assertEquals("payload", result)
    }

    @Test
    fun `execute always fails under ALWAYS_FAIL`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_FAIL) }
        val client = ApiClient(scenarios)

        // Act
        val thrown = runCatching { client.execute { "payload" } }.exceptionOrNull()

        // Assert
        assertTrue(thrown is IOException)
    }

    @Test
    fun `execute succeeds only from the third call under SUCCEED_ON_THIRD_ATTEMPT`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.SUCCEED_ON_THIRD_ATTEMPT) }
        val client = ApiClient(scenarios)

        // Act
        val firstAttempt = runCatching { client.execute { "payload" } }.exceptionOrNull()
        val secondAttempt = runCatching { client.execute { "payload" } }.exceptionOrNull()
        val thirdAttempt = client.execute { "payload" }

        // Assert
        assertTrue(firstAttempt is IOException)
        assertTrue(secondAttempt is IOException)
        assertEquals("payload", thirdAttempt)
    }

    @Test
    fun `re-selecting the same scenario restarts the attempt count`() = runTest {
        // Arrange: burn two attempts of SUCCEED_ON_THIRD_ATTEMPT, one short of success
        val scenarios = ScenarioHolder().apply { select(Scenario.SUCCEED_ON_THIRD_ATTEMPT) }
        val client = ApiClient(scenarios)
        runCatching { client.execute { "payload" } }
        runCatching { client.execute { "payload" } }

        // Act: re-tapping the same scenario chip should start the demo over
        scenarios.select(Scenario.SUCCEED_ON_THIRD_ATTEMPT)
        val afterReselect = runCatching { client.execute { "payload" } }.exceptionOrNull()

        // Assert
        assertTrue(afterReselect is IOException)
    }
}
