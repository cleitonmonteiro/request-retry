@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.createorder

import app.cash.turbine.test
import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.remote.ApiClient
import io.github.cleitonmonteiro.requestretry.data.remote.NewOrderRequestDto
import io.github.cleitonmonteiro.requestretry.data.remote.OrdersRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.data.repository.OrdersRepositoryImpl
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.usecase.CreateOrderUseCase
import io.github.cleitonmonteiro.requestretry.domain.usecase.VerifyOrderOperationUseCase
import io.github.cleitonmonteiro.requestretry.retry.OperationControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.OperationState
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
 * ViewModel test) — it's that [CreateOrderIntent.Retry] must resend the exact request that
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
        viewModel.onIntent(CreateOrderIntent.ChangeItemName("Backpack"))
        viewModel.onIntent(CreateOrderIntent.ChangeQuantity("3"))
        viewModel.onIntent(CreateOrderIntent.ChangeCustomerName("Ada"))
        viewModel.onIntent(CreateOrderIntent.Submit)
        advanceUntilIdle()

        // Assert
        val expected = Order(id = "A-2000", item = "Backpack", total = 59.97)
        assertEquals(expected, (viewModel.state.value.result as OperationState.Succeeded).data)
    }

    @Test
    fun `successful submit emits its confirmation as a one-off effect`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val viewModel = newViewModel(scenarios, mutableListOf()) {
            """{"order_id":"A-2000","item_name":"Backpack","total_amount":"59.97"}"""
        }

        viewModel.effects.test {
            // Act
            viewModel.onIntent(CreateOrderIntent.ChangeItemName("Backpack"))
            viewModel.onIntent(CreateOrderIntent.Submit)
            advanceUntilIdle()

            // Assert
            assertEquals(CreateOrderEffect.ShowOrderCreated("A-2000"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `manual retries resend the submitted snapshot with stable identity`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.SUCCEED_ON_THIRD_ATTEMPT) }
        val capturedBodies = mutableListOf<String>()
        val capturedKeys = mutableListOf<String>()
        val capturedOperationIds = mutableListOf<String>()
        val viewModel = newViewModel(scenarios, capturedBodies, capturedKeys, capturedOperationIds) {
            """{"order_id":"A-2000","item_name":"Backpack","total_amount":"59.97"}"""
        }
        viewModel.onIntent(CreateOrderIntent.ChangeItemName("Backpack"))
        viewModel.onIntent(CreateOrderIntent.ChangeQuantity("3"))
        viewModel.onIntent(CreateOrderIntent.ChangeCustomerName("Ada"))
        viewModel.onIntent(CreateOrderIntent.Submit)
        // Editing live form state cannot change the immutable session that was just submitted.
        viewModel.onIntent(CreateOrderIntent.ChangeItemName("Something else"))
        viewModel.onIntent(CreateOrderIntent.ChangeQuantity("99"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.result is OperationState.Failed)

        viewModel.onIntent(CreateOrderIntent.Retry)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.result is OperationState.Failed)

        viewModel.onIntent(CreateOrderIntent.Retry)
        advanceUntilIdle()

        assertEquals(3, capturedBodies.size)
        capturedBodies.forEach { body ->
            val request = Json.decodeFromString<NewOrderRequestDto>(body)
            assertEquals("Backpack", request.itemName)
            assertEquals(3, request.quantity)
        }
        assertEquals(3, capturedKeys.size)
        assertTrue(capturedKeys.first().isNotBlank())
        assertEquals(1, capturedKeys.distinct().size)
        assertEquals(1, capturedOperationIds.distinct().size)
        assertTrue(viewModel.state.value.result is OperationState.Succeeded)
    }

    @Test
    fun `changing the scenario after a submit does not resend the order`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val capturedBodies = mutableListOf<String>()
        val viewModel = newViewModel(scenarios, capturedBodies) {
            """{"order_id":"A-2000","item_name":"Backpack","total_amount":"59.97"}"""
        }
        viewModel.onIntent(CreateOrderIntent.ChangeItemName("Backpack"))
        viewModel.onIntent(CreateOrderIntent.Submit)
        advanceUntilIdle()
        check(viewModel.state.value.result is OperationState.Succeeded)

        // Act: tap a scenario chip after the order was already created
        viewModel.onIntent(CreateOrderIntent.SelectScenario(Scenario.CONNECTION_ERROR))
        advanceUntilIdle()

        // Assert: POST /orders is not idempotent — a scenario change must never replay it
        assertEquals(1, capturedBodies.size)
        assertEquals(
            Order(id = "A-2000", item = "Backpack", total = 59.97),
            (viewModel.state.value.result as OperationState.Succeeded).data,
        )
        assertEquals(Scenario.CONNECTION_ERROR, viewModel.state.value.scenario)
    }

    @Test
    fun `lost responses become outcome unknown and verify without a new mutation`() = runTest {
        val scenarios = ScenarioHolder().apply { select(Scenario.RESPONSE_LOST_AFTER_COMMIT) }
        val capturedBodies = mutableListOf<String>()
        val viewModel = newViewModel(scenarios, capturedBodies) {
            """{"order_id":"A-2000","item_name":"Backpack","total_amount":"19.99"}"""
        }
        viewModel.onIntent(CreateOrderIntent.ChangeItemName("Backpack"))
        viewModel.onIntent(CreateOrderIntent.Submit)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.result is OperationState.Failed)
        viewModel.onIntent(CreateOrderIntent.Retry)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.result is OperationState.Failed)
        viewModel.onIntent(CreateOrderIntent.Retry)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.result is OperationState.OutcomeUnknown)
        assertEquals(3, capturedBodies.size)

        scenarios.select(Scenario.ALWAYS_SUCCEED)
        viewModel.onIntent(CreateOrderIntent.VerifyStatus)
        advanceUntilIdle()

        assertEquals(
            "A-2000",
            (viewModel.state.value.result as OperationState.Succeeded).data.id,
        )
        assertEquals(3, capturedBodies.size)
    }

    private fun newViewModel(
        scenarios: ScenarioHolder,
        capturedBodies: MutableList<String>,
        capturedKeys: MutableList<String> = mutableListOf(),
        capturedOperationIds: MutableList<String> = mutableListOf(),
        respondBody: () -> String,
    ): CreateOrderViewModel {
        val engineConfig = MockEngineConfig().apply {
            dispatcher = Dispatchers.Unconfined
            addHandler { request ->
                if (request.url.encodedPath.startsWith("/operations/")) {
                    val operationId = request.url.encodedPath.substringAfterLast('/')
                    return@addHandler respond(
                        content = """{"operation_id":"$operationId","status":"SUCCEEDED","order":${respondBody()}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                capturedBodies += request.body.toByteArray().decodeToString()
                capturedKeys += request.headers["Idempotency-Key"].orEmpty()
                capturedOperationIds += request.headers["X-Operation-ID"].orEmpty()
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
        val repository = OrdersRepositoryImpl(OrdersRemoteDataSource(httpClient, ApiClient(scenarios)))
        return CreateOrderViewModel(
            createOrder = CreateOrderUseCase(repository),
            verifyOrder = VerifyOrderOperationUseCase(repository),
            scenarios = scenarios,
            operationControllers = OperationControllerFactory(),
        )
    }
}
