package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.FakeOrderOperationStore
import io.github.cleitonmonteiro.requestretry.domain.model.IdempotencyKey
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

class CreateOrderUseCaseTest {

    @Test
    fun `invoke returns the order created by the repository`() = runTest {
        // Arrange
        val created = Order(id = "A-1004", item = "Backpack", total = 59.97)
        val store = FakeOrderOperationStore()
        val useCase = CreateOrderUseCase(FakeOrdersRepository(created = created), store)
        val request = request(quantity = 3)

        // Act
        val result = useCase(request).first()

        // Assert
        assertEquals(created, result)
        assertEquals(io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState.SUCCEEDED, store.get(request.operationId)?.state)
    }

    @Test
    fun `invoke propagates a repository failure instead of swallowing it`() = runTest {
        // Arrange
        val store = FakeOrderOperationStore()
        val useCase = CreateOrderUseCase(FakeOrdersRepository(failure = IOException("boom")), store)
        val request = request(quantity = 1)

        // Act
        val thrown = runCatching { useCase(request).first() }.exceptionOrNull()

        // Assert
        assertTrue(thrown is IOException)
        assertEquals(
            io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState.PENDING_CONFIRMATION,
            store.get(request.operationId)?.state,
        )
    }

    private class FakeOrdersRepository(
        private val created: Order? = null,
        private val failure: Throwable? = null,
    ) : OrdersRepository {
        override fun getOrders(): Flow<List<Order>> = flowOf(emptyList())

        override fun createOrder(request: NewOrderRequest): Flow<Order> =
            failure?.let { flow { throw it } } ?: flowOf(requireNotNull(created))

        override fun getOperationStatus(operationId: OperationId): Flow<OrderOperationStatus> =
            flowOf(OrderOperationStatus.Unknown)
    }

    private fun request(quantity: Int) = NewOrderRequest(
        itemName = "Backpack",
        quantity = quantity,
        customerName = "Ada",
        operationId = OperationId("operation-$quantity"),
        idempotencyKey = IdempotencyKey("key-$quantity"),
    )
}
