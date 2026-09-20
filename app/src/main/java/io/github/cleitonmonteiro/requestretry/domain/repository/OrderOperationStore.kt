package io.github.cleitonmonteiro.requestretry.domain.repository

import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.model.StoredOrderOperation

interface OrderOperationStore {
    suspend fun createIfAbsent(request: NewOrderRequest)
    suspend fun markSending(operationId: OperationId)
    suspend fun markPendingConfirmation(operationId: OperationId)
    suspend fun markOutcomeUnknown(operationId: OperationId)
    suspend fun markSucceeded(operationId: OperationId, order: Order)
    suspend fun markRejected(operationId: OperationId, failureCode: String?)
    suspend fun get(operationId: OperationId): StoredOrderOperation?
    suspend fun pending(): List<StoredOrderOperation>
}

interface OrderReconciliationScheduler {
    fun schedule(operationId: OperationId)
}
