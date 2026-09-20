@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import app.cash.turbine.test
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
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
        val controller = RetryController(scope = this, apiCall = ApiCall { flowOf("payload") })

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
            apiCall = ApiCall<String> { flow { throw IOException("boom") } },
        )

        // Act
        controller.load()
        advanceUntilIdle()

        // Assert
        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals("We couldn’t complete your request. Try again.", feedback.message)
        assertEquals(1, feedback.attemptsUsed)
        assertEquals(3, feedback.maxAttempts)
        assertTrue(feedback.canRetry)
    }

    @Test
    fun `eager ApiCall failure becomes Feedback instead of leaving Loading`() = runTest {
        val controller = RetryController<String>(scope = this, apiCall = ApiCall { throw IOException("boom") })

        controller.load()
        advanceUntilIdle()

        assertTrue(controller.state.value is RetryUiState.Feedback)
    }

    @Test
    fun `an empty ApiCall flow becomes Feedback instead of leaving Loading`() = runTest {
        val controller = RetryController(scope = this, apiCall = ApiCall<String> { emptyFlow() })

        controller.load()
        advanceUntilIdle()

        assertTrue(controller.state.value is RetryUiState.Feedback)
    }

    @Test
    fun `retry waits out the policy delay before calling again`() = runTest {
        // Arrange
        var callCount = 0
        val api = ApiCall<String> {
            flow {
                callCount++
                if (callCount == 1) throw IOException("boom") else emit("payload")
            }
        }
        val controller = RetryController(
            scope = this,
            apiCall = api,
            retryPolicy = RetryPolicy { 3.seconds },
        )

        controller.load()
        advanceUntilIdle()

        // Act
        controller.retry()

        // Assert: retry state is visible immediately, but the API is not called during backoff.
        assertEquals(RetryUiState.Loading(retryAttempt = 2, maxRetries = 3), controller.state.value)
        assertEquals(1, callCount)
        advanceTimeBy(2.seconds)
        assertEquals(1, callCount)
        advanceUntilIdle()
        assertEquals(RetryUiState.Success("payload"), controller.state.value)
    }

    @Test
    fun `three failed retries exhaust the retry budget`() = runTest {
        // Arrange
        val controller = RetryController(
            scope = this,
            apiCall = ApiCall<String> { flow { throw IOException("boom") } },
            retryPolicy = RetryPolicy { Duration.ZERO },
        )
        controller.load()
        advanceUntilIdle()

        // Act
        repeat(2) {
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
            apiCall = ApiCall<String> { flow { throw IOException("boom") } },
            retryPolicy = RetryPolicy { Duration.ZERO },
        )
        controller.load()
        advanceUntilIdle()
        repeat(2) {
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
        val api = ApiCall<String> { flow { if (shouldFail) throw IOException("boom") else emit("payload") } }
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
        assertEquals(1, feedback.retriesUsed)
        assertTrue(feedback.canRetry)
    }

    @Test
    fun `load's Loading state carries no retry attempt info`() = runTest {
        // Arrange
        val controller = RetryController(scope = this, apiCall = ApiCall { flowOf("payload") })

        // Act: read state right after the synchronous pre-set in run(), before the call resolves
        controller.load()
        val loading = controller.state.value as RetryUiState.Loading

        // Assert: this is the first attempt, not a retry — nothing to report yet
        assertEquals(null, loading.retryAttempt)
        assertEquals(null, loading.maxRetries)

        advanceUntilIdle()
    }

    @Test
    fun `a second retry reports retryAttempt 2`() = runTest {
        // Arrange: always fails, zero delay so each retry's synchronous pre-set is easy to inspect
        val controller = RetryController(
            scope = this,
            apiCall = ApiCall<String> { flow { throw IOException("boom") } },
            retryPolicy = RetryPolicy { Duration.ZERO },
        )
        controller.load()
        advanceUntilIdle()
        controller.retry() // retry #1
        advanceUntilIdle()

        // Act
        controller.retry() // retry #2
        val loading = controller.state.value as RetryUiState.Loading

        // Assert
        assertEquals(3, loading.retryAttempt)
        assertEquals(3, loading.maxRetries)

        advanceUntilIdle()
    }

    @Test
    fun `double-tapping retry only spends one retry, even when the backoff is under a second`() = runTest {
        // Arrange: the only thing keeping a second tap from double-spending is state turning to
        // Loading synchronously, before the caller gets control back.
        var callCount = 0
        val api = ApiCall<String> { flow { callCount++; throw IOException("boom") } }
        val controller = RetryController(
            scope = this,
            apiCall = api,
            retryPolicy = RetryPolicy { 900.milliseconds },
        )
        controller.load()
        advanceUntilIdle()
        check(callCount == 1)

        // Act: tap Retry twice in a row, as a fast double-tap would
        controller.retry()
        val stateRightAfterFirstTap = controller.state.value
        controller.retry()
        advanceUntilIdle()

        // Assert: the first tap already moved state off Feedback, so the second tap's guard
        // (`current !is Feedback`) rejects it — only one retry is spent, one call is made
        assertTrue(stateRightAfterFirstTap is RetryUiState.Loading)
        assertEquals(2, callCount)
        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals(2, feedback.retriesUsed)
    }

    @Test
    fun `cancelling a superseded job does not surface a spurious Feedback`() = runTest {
        // Arrange: a call that never resolves before being superseded
        val api = ApiCall<String> { flow { delay(5.seconds); emit("payload") } }
        val controller = RetryController(scope = this, apiCall = api)

        controller.state.test {
            skipItems(1) // initial Idle

            // Act: start a call, then supersede it with a fresh load() before it resolves
            controller.load()
            controller.load()
            advanceUntilIdle()

            // Assert: a spurious Feedback from the cancelled first job would be buffered
            // ahead of this and fail the assertion — only the second call's Loading and Success follow
            assertTrue(awaitItem() is RetryUiState.Loading)
            assertEquals(RetryUiState.Success("payload"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
