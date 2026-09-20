package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.model.OrderOperationStatus
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetOrdersUseCaseTest {

    @Test
    fun `invoke returns orders sorted by total descending`() = runTest {
        // Arrange
        val unsorted = listOf(
            Order(id = "A-1", item = "Cheap", total = 10.0),
            Order(id = "A-2", item = "Expensive", total = 100.0),
            Order(id = "A-3", item = "Mid", total = 50.0),
        )
        val useCase = GetOrdersUseCase(FakeOrdersRepository(orders = unsorted))

        // Act
        val result = useCase().first()

        // Assert
        assertEquals(listOf("A-2", "A-3", "A-1"), result.map { it.id })
    }

    @Test
    fun `invoke propagates a repository failure instead of swallowing it`() = runTest {
        // Arrange: the failure now surfaces on collection, not on invoke() itself
        val useCase = GetOrdersUseCase(FakeOrdersRepository(failure = IOException("boom")))

        // Act
        val thrown = runCatching { useCase().first() }.exceptionOrNull()

        // Assert
        assertTrue(thrown is IOException)
    }

    private class FakeOrdersRepository(
        private val orders: List<Order> = emptyList(),
        private val failure: Throwable? = null,
    ) : OrdersRepository {
        override fun getOrders(): Flow<List<Order>> =
            failure?.let { flow { throw it } } ?: flowOf(orders)

        override fun createOrder(request: NewOrderRequest): Flow<Order> =
            error("not used by this test")

        override fun getOperationStatus(operationId: OperationId): Flow<OrderOperationStatus> =
            flowOf(OrderOperationStatus.Unknown)
    }
}
