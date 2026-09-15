package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetOrdersUseCase @Inject constructor(
    private val repository: OrdersRepository,
) {
    operator fun invoke(): Flow<List<Order>> =
        repository.getOrders().map { orders -> orders.sortedByDescending { it.total } }
}
