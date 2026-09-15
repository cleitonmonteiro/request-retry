@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.profile

import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.FakeApi
import io.github.cleitonmonteiro.requestretry.data.Scenario
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Thin test confirming [ProfileViewModel] wires itself to [io.github.cleitonmonteiro.requestretry.retry.RetryController]
 * correctly — the retry/backoff behavior itself is covered by RetryControllerTest.
 */
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `view model loads on init and forwards retry to the controller`() = runTest {
        // Arrange
        val api = FakeApi<UserProfile> { sampleProfile }
        api.scenario = Scenario.ALWAYS_FAIL
        val viewModel = ProfileViewModel(api)
        advanceUntilIdle()
        api.scenario = Scenario.ALWAYS_SUCCEED

        // Act
        viewModel.retry()
        advanceUntilIdle()

        // Assert
        assertEquals(RetryUiState.Success(sampleProfile), viewModel.state.value)
    }
}
