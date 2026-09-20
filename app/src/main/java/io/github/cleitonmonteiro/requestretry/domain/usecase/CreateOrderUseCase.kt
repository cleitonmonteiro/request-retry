package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class CreateOrderUseCase @Inject constructor(
    private val repository: OrdersRepository,
) {
    operator fun invoke(request: NewOrderRequest): Flow<Order> = repository.createOrder(request)
}
