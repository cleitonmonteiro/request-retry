@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.profile

import app.cash.turbine.test
import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.remote.ApiClient
import io.github.cleitonmonteiro.requestretry.data.remote.ProfileRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.repository.ProfileRepositoryImpl
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetProfileUseCase
import io.github.cleitonmonteiro.requestretry.retry.OperationControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Thin test confirming [ProfileViewModel] wires itself to [io.github.cleitonmonteiro.requestretry.retry.OperationController]
 * correctly — the retry/backoff behavior itself is covered by OperationControllerTest and
 * FailurePolicyTest, and ApiClient's transport-error mapping by ApiClientTest. Failures here are
 * driven by the real (mocked-transport) server response rather than any in-app fault injection.
 */
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `view model loads on init and forwards retry to the use case`() = runTest {
        // Arrange: the server rejects the first attempt, then accepts the retry.
        var calls = 0
        val viewModel = newViewModel { if (++calls == 1) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK }
        advanceUntilIdle()
        assertTrue(viewModel.state.value is OperationState.Failed)

        // Act
        viewModel.onIntent(ProfileIntent.Retry)
        advanceUntilIdle()

        // Assert
        val expected = UserProfile(name = "Ada Lovelace", email = "ada@example.com")
        assertEquals(expected, (viewModel.state.value as OperationState.Succeeded).data)
    }

    @Test
    fun `leave emits a navigation effect`() = runTest {
        // Arrange
        val viewModel = newViewModel { HttpStatusCode.OK }

        viewModel.effects.test {
            // Act
            viewModel.onIntent(ProfileIntent.Leave)

            // Assert
            assertEquals(ProfileEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun newViewModel(status: () -> HttpStatusCode): ProfileViewModel {
        val engineConfig = MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler {
                respond(
                    content = """{"full_name":"Ada Lovelace","email_address":"ada@example.com"}""",
                    status = status(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val httpClient = HttpClient(MockEngine(engineConfig)) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
            }
        }
        val repository = ProfileRepositoryImpl(ProfileRemoteDataSource(httpClient, ApiClient()))
        return ProfileViewModel(GetProfileUseCase(repository), OperationControllerFactory())
    }
}
