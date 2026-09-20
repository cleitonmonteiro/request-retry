package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderOperationStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single

class CreateOrderUseCase @Inject constructor(
    private val repository: OrdersRepository,
    private val operations: OrderOperationStore,
) {
    operator fun invoke(request: NewOrderRequest): Flow<Order> = flow {
        operations.createIfAbsent(request)
        operations.markSending(request.operationId)
        val order = try {
            repository.createOrder(request).single()
        } catch (error: Throwable) {
            try {
                operations.markPendingConfirmation(request.operationId)
            } catch (persistenceError: Throwable) {
                error.addSuppressed(persistenceError)
            }
            throw error
        }
        operations.markSucceeded(request.operationId, order)
        emit(order)
    }
}
