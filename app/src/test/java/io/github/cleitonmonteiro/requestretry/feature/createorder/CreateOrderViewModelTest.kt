@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.createorder

import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.remote.ApiClient
import io.github.cleitonmonteiro.requestretry.data.remote.NewOrderRequestDto
import io.github.cleitonmonteiro.requestretry.data.remote.OrdersRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.data.repository.OrdersRepositoryImpl
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.usecase.CreateOrderUseCase
import io.github.cleitonmonteiro.requestretry.retry.RetryControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.RetryPolicy
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Duration
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
 * [CreateOrderViewModel] wires a multi-field form into a single [io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest].
 * The interesting case here isn't the happy path (covered generically by every other
 * ViewModel test) — it's that [CreateOrderViewModel.retry] must resend the exact request that
 * was submitted, even if the form has since been edited without a new submit.
 */
class CreateOrderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `submit creates an order from the form input`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val capturedBodies = mutableListOf<String>()
        val viewModel = newViewModel(scenarios, capturedBodies) {
            """{"order_id":"A-2000","item_name":"Backpack","total_amount":"59.97"}"""
        }

        // Act
        viewModel.onItemNameChanged("Backpack")
        viewModel.onQuantityChanged("3")
        viewModel.onCustomerNameChanged("Ada")
        viewModel.submit()
        advanceUntilIdle()

        // Assert
        val expected = Order(id = "A-2000", item = "Backpack", total = 59.97)
        assertEquals(RetryUiState.Success(expected), viewModel.state.value.result)
        assertTrue(viewModel.state.value.hasSubmitted)
    }

    @Test
    fun `retry resends the originally submitted request, not the edited form`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_FAIL) }
        val capturedBodies = mutableListOf<String>()
        val viewModel = newViewModel(scenarios, capturedBodies) {
            """{"order_id":"A-2000","item_name":"Backpack","total_amount":"59.97"}"""
        }
        viewModel.onItemNameChanged("Backpack")
        viewModel.onQuantityChanged("3")
        viewModel.onCustomerNameChanged("Ada")
        viewModel.submit()
        advanceUntilIdle()
        check(viewModel.state.value.result is RetryUiState.Feedback)

        // Act: edit the form without submitting again, then retry
        viewModel.onItemNameChanged("Something else")
        viewModel.onQuantityChanged("99")
        viewModel.retry()
        advanceUntilIdle()

        // Assert: both requests that went out were for the original submission
        assertEquals(2, capturedBodies.size)
        capturedBodies.forEach { body ->
            val request = Json.decodeFromString<NewOrderRequestDto>(body)
            assertEquals("Backpack", request.itemName)
            assertEquals(3, request.quantity)
        }
    }

    @Test
    fun `changing the scenario after a submit does not resend the order`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val capturedBodies = mutableListOf<String>()
        val viewModel = newViewModel(scenarios, capturedBodies) {
            """{"order_id":"A-2000","item_name":"Backpack","total_amount":"59.97"}"""
        }
        viewModel.onItemNameChanged("Backpack")
        viewModel.submit()
        advanceUntilIdle()
        check(viewModel.state.value.result is RetryUiState.Success)

        // Act: tap a scenario chip after the order was already created
        viewModel.setScenario(Scenario.ALWAYS_FAIL)
        advanceUntilIdle()

        // Assert: POST /orders is not idempotent — a scenario change must never replay it
        assertEquals(1, capturedBodies.size)
        assertEquals(RetryUiState.Success(Order(id = "A-2000", item = "Backpack", total = 59.97)), viewModel.state.value.result)
        assertEquals(Scenario.ALWAYS_FAIL, viewModel.state.value.scenario)
    }

    private fun newViewModel(
        scenarios: ScenarioHolder,
        capturedBodies: MutableList<String>,
        respondBody: () -> String,
    ): CreateOrderViewModel {
        val engineConfig = MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler { request ->
                capturedBodies += request.body.toByteArray().decodeToString()
                respond(
                    content = respondBody(),
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        val httpClient = HttpClient(MockEngine(engineConfig)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
            }
        }
        val createOrder = CreateOrderUseCase(OrdersRepositoryImpl(OrdersRemoteDataSource(httpClient, ApiClient(scenarios))))
        val retryControllers = RetryControllerFactory(RetryPolicy { Duration.ZERO })
        return CreateOrderViewModel(createOrder, scenarios, retryControllers)
    }
}
