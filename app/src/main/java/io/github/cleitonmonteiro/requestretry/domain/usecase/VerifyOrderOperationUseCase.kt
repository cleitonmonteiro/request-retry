package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.OrderOperationStatus
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.single

class VerifyOrderOperationUseCase @Inject constructor(
    private val repository: OrdersRepository,
) {
    suspend operator fun invoke(operationId: OperationId): OrderOperationStatus =
        repository.getOperationStatus(operationId).single()
}
