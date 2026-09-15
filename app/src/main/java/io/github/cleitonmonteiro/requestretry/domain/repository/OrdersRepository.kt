package io.github.cleitonmonteiro.requestretry.domain.repository

import io.github.cleitonmonteiro.requestretry.domain.model.Order

interface OrdersRepository {
    suspend fun getOrders(): List<Order>
}
