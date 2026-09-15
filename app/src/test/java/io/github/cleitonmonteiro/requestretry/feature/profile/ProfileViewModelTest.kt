@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.profile

import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.repository.ProfileRepository
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetProfileUseCase
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import java.io.IOException
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
    fun `view model loads on init and forwards retry to the use case`() = runTest {
        // Arrange
        val profile = UserProfile(name = "Ada Lovelace", email = "ada@example.com")
        var shouldFail = true
        val repository = object : ProfileRepository {
            override suspend fun getProfile(): UserProfile {
                if (shouldFail) throw IOException("boom")
                return profile
            }
        }
        val viewModel = ProfileViewModel(GetProfileUseCase(repository), ScenarioHolder())
        advanceUntilIdle()
        shouldFail = false

        // Act
        viewModel.retry()
        advanceUntilIdle()

        // Assert
        assertEquals(RetryUiState.Success(profile), viewModel.state.value)
    }
}
