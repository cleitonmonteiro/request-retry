@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import app.cash.turbine.test
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryControllerTest {

    @Test
    fun `load emits Success after the call succeeds`() = runTest {
        // Arrange
        val controller = RetryController(scope = this, apiCall = ApiCall { "payload" })

        // Act
        controller.load()
        advanceUntilIdle()

        // Assert
        assertEquals(RetryUiState.Success("payload"), controller.state.value)
    }

    @Test
    fun `load emits a retryable Feedback after the call fails`() = runTest {
        // Arrange
        val controller = RetryController(
            scope = this,
            apiCall = ApiCall<String> { throw IOException("boom") },
        )

        // Act
        controller.load()
        advanceUntilIdle()

        // Assert
        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals("boom", feedback.message)
        assertEquals(0, feedback.retriesUsed)
        assertEquals(3, feedback.maxRetries)
        assertTrue(feedback.canRetry)
    }

    @Test
    fun `retry waits out the policy delay before calling again, reporting a countdown along the way`() = runTest {
        // Arrange
        var callCount = 0
        val api = ApiCall<String> {
            callCount++
            if (callCount == 1) throw IOException("boom") else "payload"
        }
        val controller = RetryController(
            scope = this,
            apiCall = api,
            retryPolicy = RetryPolicy { 3.seconds },
        )

        controller.state.test {
            skipItems(1) // initial Loading()
            controller.load()
            awaitItem() // failed first attempt -> Feedback

            // Act
            controller.retry()
            val callCountBeforeDelayElapses = callCount

            // Assert
            assertEquals(1, callCountBeforeDelayElapses)
            assertEquals(3, (awaitItem() as RetryUiState.Loading).backoffSecondsRemaining)
            assertEquals(2, (awaitItem() as RetryUiState.Loading).backoffSecondsRemaining)
            assertEquals(1, (awaitItem() as RetryUiState.Loading).backoffSecondsRemaining)
            assertEquals(null, (awaitItem() as RetryUiState.Loading).backoffSecondsRemaining)
            assertEquals(RetryUiState.Success("payload"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `three failed retries exhaust the retry budget`() = runTest {
        // Arrange
        val controller = RetryController(
            scope = this,
            apiCall = ApiCall<String> { throw IOException("boom") },
            retryPolicy = RetryPolicy { Duration.ZERO },
        )
        controller.load()
        advanceUntilIdle()

        // Act
        repeat(3) {
            controller.retry()
            advanceUntilIdle()
        }

        // Assert
        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals(3, feedback.retriesUsed)
        assertFalse(feedback.canRetry)
    }

    @Test
    fun `retry does nothing once the retry budget is spent`() = runTest {
        // Arrange
        val controller = RetryController(
            scope = this,
            apiCall = ApiCall<String> { throw IOException("boom") },
            retryPolicy = RetryPolicy { Duration.ZERO },
        )
        controller.load()
        advanceUntilIdle()
        repeat(3) {
            controller.retry()
            advanceUntilIdle()
        }
        val exhaustedState = controller.state.value

        // Act
        controller.retry()
        advanceUntilIdle()

        // Assert
        assertEquals(exhaustedState, controller.state.value)
    }

    @Test
    fun `load resets the retry budget after a previous session recovered`() = runTest {
        // Arrange
        var shouldFail = true
        val api = ApiCall<String> { if (shouldFail) throw IOException("boom") else "payload" }
        val controller = RetryController(
            scope = this,
            apiCall = api,
            retryPolicy = RetryPolicy { Duration.ZERO },
        )
        controller.load()
        advanceUntilIdle()
        controller.retry()
        advanceUntilIdle()
        shouldFail = false
        controller.retry()
        advanceUntilIdle()
        shouldFail = true

        // Act
        controller.load()
        advanceUntilIdle()

        // Assert
        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals(0, feedback.retriesUsed)
        assertTrue(feedback.canRetry)
    }
}
