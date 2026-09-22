package io.github.cleitonmonteiro.requestretry.domain.repository

import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.OrderOperationStatus
import kotlinx.coroutines.flow.Flow

interface OrdersRepository {
    fun createOrder(request: NewOrderRequest): Flow<Order>
    fun getOperationStatus(operationId: OperationId): Flow<OrderOperationStatus>
}
