package io.github.cleitonmonteiro.requestretry

import io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.model.StoredOrderOperation
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderOperationStore
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderReconciliationScheduler

class FakeOrderOperationStore : OrderOperationStore {
    private val values = mutableMapOf<OperationId, StoredOrderOperation>()
    private val requests = mutableMapOf<OperationId, NewOrderRequest>()

    override suspend fun createIfAbsent(request: NewOrderRequest) {
        val existing = values[request.operationId]
        if (existing != null) {
            require(requests[request.operationId] == request)
            return
        }
        val now = System.currentTimeMillis()
        requests[request.operationId] = request
        values[request.operationId] = StoredOrderOperation(
            operationId = request.operationId,
            idempotencyKey = request.idempotencyKey,
            state = DurableOperationState.CREATED,
            attemptsUsed = 0,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
    }

    override suspend fun markSending(operationId: OperationId) = update(operationId) {
        it.copy(state = DurableOperationState.SENDING, attemptsUsed = it.attemptsUsed + 1)
    }

    override suspend fun markPendingConfirmation(operationId: OperationId) = update(operationId) {
        it.copy(state = DurableOperationState.PENDING_CONFIRMATION)
    }

    override suspend fun markOutcomeUnknown(operationId: OperationId) = update(operationId) {
        it.copy(state = DurableOperationState.OUTCOME_UNKNOWN)
    }

    override suspend fun markSucceeded(operationId: OperationId, order: Order) = update(operationId) {
        it.copy(state = DurableOperationState.SUCCEEDED, order = order)
    }

    override suspend fun markRejected(operationId: OperationId, failureCode: String?) = update(operationId) {
        it.copy(state = DurableOperationState.REJECTED, failureCode = failureCode)
    }

    override suspend fun get(operationId: OperationId): StoredOrderOperation? = values[operationId]
    override suspend fun pending(): List<StoredOrderOperation> = values.values.filter {
        it.state !in setOf(DurableOperationState.SUCCEEDED, DurableOperationState.REJECTED, DurableOperationState.EXPIRED)
    }

    private fun update(operationId: OperationId, transform: (StoredOrderOperation) -> StoredOrderOperation) {
        values[operationId] = transform(requireNotNull(values[operationId])).copy(updatedAtEpochMillis = System.currentTimeMillis())
    }
}

class FakeOrderReconciliationScheduler : OrderReconciliationScheduler {
    val scheduled = mutableListOf<OperationId>()
    override fun schedule(operationId: OperationId) {
        scheduled += operationId
    }
}
