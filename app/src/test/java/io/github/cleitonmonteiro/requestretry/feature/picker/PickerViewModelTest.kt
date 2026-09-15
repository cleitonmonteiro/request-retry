@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.picker

import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.remote.ApiClient
import io.github.cleitonmonteiro.requestretry.data.remote.ItemsRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.data.remote.mockHttpClient
import io.github.cleitonmonteiro.requestretry.data.repository.ItemsRepositoryImpl
import io.github.cleitonmonteiro.requestretry.domain.model.Action
import io.github.cleitonmonteiro.requestretry.domain.model.ActionType
import io.github.cleitonmonteiro.requestretry.domain.model.Item
import io.github.cleitonmonteiro.requestretry.domain.usecase.GetItemsUseCase
import io.github.cleitonmonteiro.requestretry.domain.usecase.SendItemUseCase
import io.github.cleitonmonteiro.requestretry.retry.RetryControllerFactory
import io.github.cleitonmonteiro.requestretry.retry.RetryPolicy
import io.github.cleitonmonteiro.requestretry.retry.RetryUiState
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Thin test confirming [PickerViewModel] wires its two [io.github.cleitonmonteiro.requestretry.retry.RetryController]s
 * correctly — the retry/backoff behavior itself is covered by RetryControllerTest, and
 * ApiClient's scenario handling by ApiClientTest.
 *
 * Wires the same real repository chain twice — once per use case, sharing one mocked
 * [io.ktor.client.HttpClient] but each with its own [ApiClient] — exactly mirroring what Hilt
 * actually builds, since `HttpClient` is `@Singleton` but `ApiClient` is unscoped.
 */
class PickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `items load on init`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val viewModel = newViewModel(scenarios)

        // Act
        advanceUntilIdle()

        // Assert
        val items = (viewModel.state.value.items as RetryUiState.Success).data
        assertEquals(listOf("Backpack", "Water bottle", "Notebook"), items.map { it.name })
    }

    @Test
    fun `selecting an item triggers and completes the send request`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val viewModel = newViewModel(scenarios)
        advanceUntilIdle()
        val item = (viewModel.state.value.items as RetryUiState.Success).data.first()

        // Act
        viewModel.selectItem(item)
        advanceUntilIdle()

        // Assert: I-1's mocked response carries a DEEPLINK action, per newViewModel's routing
        val expected = Action(type = ActionType.DEEPLINK, target = "requestretry://orders")
        assertEquals(RetryUiState.Success(expected), viewModel.state.value.send)
    }

    @Test
    fun `retrySend does not touch the items controller`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_FAIL) }
        val viewModel = newViewModel(scenarios)
        advanceUntilIdle()
        val itemsFeedbackBeforeSend = viewModel.state.value.items
        check(itemsFeedbackBeforeSend is RetryUiState.Feedback)
        scenarios.select(Scenario.ALWAYS_SUCCEED)

        // Act: selecting an item starts sendController for the first time
        val fakeItem = Item(id = "I-1", name = "Backpack")
        viewModel.selectItem(fakeItem)
        advanceUntilIdle()

        // Assert
        val expected = Action(type = ActionType.DEEPLINK, target = "requestretry://orders")
        assertEquals(RetryUiState.Success(expected), viewModel.state.value.send)
        assertEquals(itemsFeedbackBeforeSend, viewModel.state.value.items)
        assertTrue(viewModel.state.value.items is RetryUiState.Feedback)
    }

    private fun newViewModel(scenarios: ScenarioHolder): PickerViewModel {
        val httpClient = mockHttpClient { path ->
            when {
                path == "/items" ->
                    """[{"item_id":"I-1","item_name":"Backpack"},""" +
                        """{"item_id":"I-2","item_name":"Water bottle"},""" +
                        """{"item_id":"I-3","item_name":"Notebook"}]"""
                path.startsWith("/items/") && path.endsWith("/send") -> {
                    val id = path.removePrefix("/items/").removeSuffix("/send")
                    """{"item_id":"$id","action":{"action_type":"deeplink","target":"requestretry://orders"}}"""
                }
                else -> error("Unexpected request path: $path")
            }
        }
        val getItems = GetItemsUseCase(ItemsRepositoryImpl(ItemsRemoteDataSource(httpClient, ApiClient(scenarios))))
        val sendItem = SendItemUseCase(ItemsRepositoryImpl(ItemsRemoteDataSource(httpClient, ApiClient(scenarios))))
        val retryControllers = RetryControllerFactory(RetryPolicy { Duration.ZERO })
        return PickerViewModel(getItems, sendItem, scenarios, retryControllers)
    }
}
