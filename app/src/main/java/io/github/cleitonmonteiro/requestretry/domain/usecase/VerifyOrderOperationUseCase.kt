package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.OrderOperationStatus
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderOperationStore
import io.github.cleitonmonteiro.requestretry.domain.repository.OrdersRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.single

class VerifyOrderOperationUseCase @Inject constructor(
    private val repository: OrdersRepository,
    private val operations: OrderOperationStore,
) {
    suspend operator fun invoke(operationId: OperationId): OrderOperationStatus {
        val status = repository.getOperationStatus(operationId).single()
        when (status) {
            OrderOperationStatus.Processing -> operations.markPendingConfirmation(operationId)
            is OrderOperationStatus.Succeeded -> operations.markSucceeded(operationId, status.order)
            is OrderOperationStatus.Rejected -> operations.markRejected(operationId, status.code)
            OrderOperationStatus.Unknown -> operations.markOutcomeUnknown(operationId)
        }
        return status
    }
}
