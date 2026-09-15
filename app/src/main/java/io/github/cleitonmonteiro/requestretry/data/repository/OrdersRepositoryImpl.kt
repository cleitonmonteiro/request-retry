package io.github.cleitonmonteiro.requestretry.data.repository

import io.github.cleitonmonteiro.requestretry.data.mapper.toDomain
import io.github.cleitonmonteiro.requestretry.data.remote.OrdersRemoteDataSource
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import javax.inject.Inject

class OrdersRepositoryImpl @Inject constructor(
    private val remote: OrdersRemoteDataSource,
) : OrdersRepository {
    override suspend fun getOrders(): List<Order> = remote.fetchOrders().map { it.toDomain() }
}
