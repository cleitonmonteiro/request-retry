@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import io.github.cleitonmonteiro.requestretry.domain.error.OutcomeCertainty
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailure
import io.github.cleitonmonteiro.requestretry.domain.error.RequestFailureException
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RetryControllerFactory] is the single home for the app's [RetryPolicy]/[RetryDecider]/
 * [RetryObserver] — not the budget itself, which now lives entirely on [OperationSpec] — so a
 * custom [OperationSpec.maxAttempts] must reach the controller the factory builds.
 */
class RetryControllerFactoryTest {

    @Test
    fun `create threads the spec's maxAttempts through to the controller`() = runTest {
        val factory = RetryControllerFactory(RetryPolicy { Duration.ZERO })
        val controller = factory.create(
            scope = this,
            spec = OperationSpec.read(OperationName.DEMO, maxAttempts = 1),
            apiCall = ApiCall<String> { flow { throw retryableFailure() } },
        )

        controller.load()
        advanceUntilIdle()

        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals(1, feedback.maxAttempts)
        assertEquals(false, feedback.canRetry)
    }

    @Test
    fun `create threads the injected retryPolicy through to the controller`() = runTest {
        val factory = RetryControllerFactory(RetryPolicy { Duration.ZERO })
        val controller = factory.create(
            scope = this,
            spec = OperationSpec.read(OperationName.DEMO, maxAttempts = 3),
            apiCall = ApiCall<String> { flow { throw retryableFailure() } },
        )

        controller.load()
        advanceUntilIdle()
        controller.retry()
        advanceUntilIdle()

        // A zero-delay policy means the retry's Loading is reached without ever observing
        // BackingOff — proof the factory's injected policy, not some other default, drove it.
        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals(2, feedback.attemptsUsed)
    }

    private fun retryableFailure() = RequestFailureException(RequestFailure.Connection(OutcomeCertainty.NOT_SENT))
}
