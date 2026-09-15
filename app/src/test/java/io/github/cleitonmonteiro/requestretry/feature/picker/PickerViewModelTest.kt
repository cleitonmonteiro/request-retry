@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.cleitonmonteiro.requestretry.feature.picker

import io.github.cleitonmonteiro.requestretry.MainDispatcherRule
import io.github.cleitonmonteiro.requestretry.data.remote.FakeNetwork
import io.github.cleitonmonteiro.requestretry.data.remote.ItemsRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.data.repository.ItemsRepositoryImpl
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
 * FakeNetwork's scenario handling by FakeNetworkTest.
 *
 * Wires the same real repository chain twice — once per use case — exactly mirroring what
 * Hilt actually builds, since each use case resolves its own unscoped FakeNetwork instance.
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

        // Assert
        assertEquals(RetryUiState.Success(Unit), viewModel.state.value.send)
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
        assertEquals(RetryUiState.Success(Unit), viewModel.state.value.send)
        assertEquals(itemsFeedbackBeforeSend, viewModel.state.value.items)
        assertTrue(viewModel.state.value.items is RetryUiState.Feedback)
    }

    private fun newViewModel(scenarios: ScenarioHolder): PickerViewModel {
        val getItems = GetItemsUseCase(ItemsRepositoryImpl(ItemsRemoteDataSource(FakeNetwork(scenarios))))
        val sendItem = SendItemUseCase(ItemsRepositoryImpl(ItemsRemoteDataSource(FakeNetwork(scenarios))))
        val retryControllers = RetryControllerFactory(RetryPolicy { Duration.ZERO })
        return PickerViewModel(getItems, sendItem, scenarios, retryControllers)
    }
}
