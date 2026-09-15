@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.profile

import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.remote.FakeNetwork
import io.github.cleitonmonteiro.requestretry.data.remote.ProfileRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.data.repository.ProfileRepositoryImpl
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetProfileUseCase
import io.github.cleitonmonteiro.requestretry.retry.RetryControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.RetryPolicy
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Thin test confirming [ProfileViewModel] wires itself to [io.github.cleitonmonteiro.requestretry.retry.RetryController]
 * and [ScenarioHolder] correctly — the retry/backoff behavior itself is covered by
 * RetryControllerTest, and FakeNetwork's scenario handling by FakeNetworkTest.
 */
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `view model loads on init and forwards retry to the use case`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_FAIL) }
        val viewModel = newViewModel(scenarios)
        advanceUntilIdle()
        scenarios.select(Scenario.ALWAYS_SUCCEED)

        // Act
        viewModel.retry()
        advanceUntilIdle()

        // Assert
        val expected = UserProfile(name = "Ada Lovelace", email = "ada@example.com")
        assertEquals(RetryUiState.Success(expected), viewModel.state.value.request)
    }

    @Test
    fun `setScenario updates the shared holder and reloads with a fresh budget`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val viewModel = newViewModel(scenarios)
        advanceUntilIdle()

        // Act
        viewModel.setScenario(Scenario.ALWAYS_FAIL)
        advanceUntilIdle()

        // Assert
        assertEquals(Scenario.ALWAYS_FAIL, scenarios.scenario.value)
        assertEquals(Scenario.ALWAYS_FAIL, viewModel.state.value.scenario)
        val feedback = viewModel.state.value.request as RetryUiState.Feedback
        assertEquals(0, feedback.retriesUsed)
    }

    private fun newViewModel(scenarios: ScenarioHolder): ProfileViewModel {
        val repository = ProfileRepositoryImpl(ProfileRemoteDataSource(FakeNetwork(scenarios)))
        // A zero-delay policy keeps this test deterministic and independent of the jittered
        // production default — the payoff of RetryController going through an injected factory.
        val retryControllers = RetryControllerFactory { Duration.ZERO }
        return ProfileViewModel(GetProfileUseCase(repository), scenarios, retryControllers)
    }
}
