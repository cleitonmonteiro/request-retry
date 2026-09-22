@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.profile

import app.cash.turbine.test
import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.remote.ApiClient
import io.github.cleitonmonteiro.requestretry.data.remote.ProfileRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.data.remote.mockHttpClient
import io.github.cleitonmonteiro.requestretry.data.repository.ProfileRepositoryImpl
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetProfileUseCase
import io.github.cleitonmonteiro.requestretry.retry.OperationControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Thin test confirming [ProfileViewModel] wires itself to [io.github.cleitonmonteiro.requestretry.retry.OperationController]
 * and [ScenarioHolder] correctly — the retry/backoff behavior itself is covered by
 * OperationControllerTest and FailurePolicyTest, and ApiClient's scenario handling by ApiClientTest.
 */
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `view model loads on init and forwards retry to the use case`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.CONNECTION_ERROR) }
        val viewModel = newViewModel(scenarios)
        advanceUntilIdle()
        scenarios.select(Scenario.ALWAYS_SUCCEED)

        // Act
        viewModel.onIntent(ProfileIntent.Retry)
        advanceUntilIdle()

        // Assert
        val expected = UserProfile(name = "Ada Lovelace", email = "ada@example.com")
        assertEquals(expected, (viewModel.state.value.request as OperationState.Succeeded).data)
    }

    @Test
    fun `setScenario updates the shared holder and reloads the profile`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val viewModel = newViewModel(scenarios)
        advanceUntilIdle()

        // Act
        viewModel.onIntent(ProfileIntent.SelectScenario(Scenario.CONNECTION_ERROR))
        advanceUntilIdle()

        // Assert
        assertEquals(Scenario.CONNECTION_ERROR, scenarios.scenario.value)
        assertEquals(Scenario.CONNECTION_ERROR, viewModel.state.value.scenario)
        val feedback = viewModel.state.value.request as OperationState.Failed
        assertEquals(1, feedback.attemptsUsed)
    }

    @Test
    fun `leave emits a navigation effect`() = runTest {
        // Arrange
        val viewModel = newViewModel(ScenarioHolder())

        viewModel.effects.test {
            // Act
            viewModel.onIntent(ProfileIntent.Leave)

            // Assert
            assertEquals(ProfileEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun newViewModel(scenarios: ScenarioHolder): ProfileViewModel {
        val httpClient = mockHttpClient { """{"full_name":"Ada Lovelace","email_address":"ada@example.com"}""" }
        val repository = ProfileRepositoryImpl(ProfileRemoteDataSource(httpClient, ApiClient(scenarios)))
        // A zero-delay policy keeps this test deterministic and independent of the jittered
        // production default — the payoff of OperationController going through an injected factory.
        return ProfileViewModel(GetProfileUseCase(repository), scenarios, OperationControllerFactory())
    }
}
