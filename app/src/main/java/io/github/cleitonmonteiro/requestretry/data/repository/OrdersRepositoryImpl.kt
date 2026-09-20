package io.github.cleitonmonteiro.requestretry.data.repository

import io.github.cleitonmonteiro.requestretry.data.mapper.toDomain
import io.github.cleitonmonteiro.requestretry.data.mapper.toDto
import io.github.cleitonmonteiro.requestretry.data.remote.OrdersRemoteDataSource
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OrdersRepositoryImpl @Inject constructor(
    private val remote: OrdersRemoteDataSource,
) : OrdersRepository {
    override fun getOrders(): Flow<List<Order>> = flow { emit(remote.fetchOrders().map { it.toDomain() }) }

    override fun createOrder(request: NewOrderRequest): Flow<Order> =
        flow { emit(remote.createOrder(request.toDto(), request.idempotencyKey).toDomain()) }
}
