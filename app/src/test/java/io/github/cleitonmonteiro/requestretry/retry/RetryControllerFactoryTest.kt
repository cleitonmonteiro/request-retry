@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.retry

import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RetryControllerFactory] is the single home for backoff *and* budget tuning — not just the
 * policy — so a custom [RetryControllerFactory.create]'s `maxRetries` must reach the controller
 * it builds, and omitting it must fall back to [RetryControllerFactory.DEFAULT_MAX_RETRIES].
 */
class RetryControllerFactoryTest {

    @Test
    fun `create defaults maxRetries to DEFAULT_MAX_RETRIES`() = runTest {
        val factory = RetryControllerFactory(RetryPolicy { kotlin.time.Duration.ZERO })
        val controller = factory.create(scope = this, apiCall = ApiCall<String> { flow { throw IOException("boom") } })

        controller.load()
        advanceUntilIdle()

        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals(RetryControllerFactory.DEFAULT_MAX_RETRIES, feedback.maxRetries)
    }

    @Test
    fun `create threads a custom maxRetries through to the controller`() = runTest {
        val factory = RetryControllerFactory(RetryPolicy { kotlin.time.Duration.ZERO })
        val controller = factory.create(
            scope = this,
            maxRetries = 1,
            apiCall = ApiCall<String> { flow { throw IOException("boom") } },
        )

        controller.load()
        advanceUntilIdle()

        val feedback = controller.state.value as RetryUiState.Feedback
        assertEquals(1, feedback.maxRetries)
        assertEquals(true, feedback.canRetry)
    }
}
