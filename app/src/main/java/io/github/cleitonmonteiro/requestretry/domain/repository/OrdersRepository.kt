package io.github.cleitonmonteiro.requestretry.domain.repository

import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import kotlinx.coroutines.flow.Flow

/** Domain contract for order creation. */
interface OrdersRepository {
    fun createOrder(request: NewOrderRequest): Flow<Order>
}
