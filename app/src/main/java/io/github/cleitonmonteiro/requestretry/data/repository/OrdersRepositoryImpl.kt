package io.github.cleitonmonteiro.requestretry.data.repository

import io.github.cleitonmonteiro.requestretry.data.mapper.toDomain
import io.github.cleitonmonteiro.requestretry.data.mapper.toDto
import io.github.cleitonmonteiro.requestretry.data.remote.OrdersRemoteDataSource
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.model.OrderOperationStatus
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OrdersRepositoryImpl @Inject constructor(
    private val remote: OrdersRemoteDataSource,
) : OrdersRepository {
    override fun createOrder(request: NewOrderRequest): Flow<Order> =
        flow {
            emit(
                remote.createOrder(
                    request = request.toDto(),
                    operationId = request.operationId.value,
                    idempotencyKey = request.idempotencyKey.value,
                ).toDomain(),
            )
        }

    override fun getOperationStatus(operationId: OperationId): Flow<OrderOperationStatus> = flow {
        emit(remote.fetchOperationStatus(operationId.value).toDomain())
    }
}
