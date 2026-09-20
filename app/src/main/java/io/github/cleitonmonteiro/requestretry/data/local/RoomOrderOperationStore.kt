package io.github.cleitonmonteiro.requestretry.data.local

import androidx.room.withTransaction
import io.github.cleitonmonteiro.requestretry.domain.model.DurableOperationState
import io.github.cleitonmonteiro.requestretry.domain.model.IdempotencyKey
import io.github.cleitonmonteiro.requestretry.domain.model.NewOrderRequest
import io.github.cleitonmonteiro.requestretry.domain.model.OperationId
import io.github.cleitonmonteiro.requestretry.domain.model.Order
import io.github.cleitonmonteiro.requestretry.domain.model.StoredOrderOperation
import io.github.cleitonmonteiro.requestretry.domain.repository.OrderOperationStore
import java.security.MessageDigest
import javax.inject.Inject

class RoomOrderOperationStore @Inject constructor(
    private val database: RequestRetryDatabase,
    private val dao: OrderOperationDao,
) : OrderOperationStore {
    override suspend fun createIfAbsent(request: NewOrderRequest) {
        database.withTransaction {
            val existing = dao.get(request.operationId.value)
            val fingerprint = request.fingerprint()
            if (existing != null) {
                require(existing.idempotencyKey == request.idempotencyKey.value) { "Operation identity mismatch" }
                require(existing.payloadFingerprint == fingerprint) { "Operation payload mismatch" }
                return@withTransaction
            }
            val now = System.currentTimeMillis()
            dao.insert(
                OrderOperationEntity(
                    operationId = request.operationId.value,
                    idempotencyKey = request.idempotencyKey.value,
                    payloadFingerprint = fingerprint,
                    state = DurableOperationState.CREATED.name,
                    attemptsUsed = 0,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    override suspend fun markSending(operationId: OperationId) {
        dao.markSending(operationId.value, System.currentTimeMillis())
    }

    override suspend fun markPendingConfirmation(operationId: OperationId) {
        dao.markNonTerminal(operationId.value, DurableOperationState.PENDING_CONFIRMATION.name, System.currentTimeMillis())
    }

    override suspend fun markOutcomeUnknown(operationId: OperationId) {
        dao.markNonTerminal(operationId.value, DurableOperationState.OUTCOME_UNKNOWN.name, System.currentTimeMillis())
    }

    override suspend fun markSucceeded(operationId: OperationId, order: Order) {
        dao.markSucceeded(operationId.value, order.id, order.item, order.total, System.currentTimeMillis())
    }

    override suspend fun markRejected(operationId: OperationId, failureCode: String?) {
        dao.markRejected(operationId.value, failureCode, System.currentTimeMillis())
    }

    override suspend fun get(operationId: OperationId): StoredOrderOperation? = dao.get(operationId.value)?.toDomain()

    override suspend fun pending(): List<StoredOrderOperation> = dao.pending().map(OrderOperationEntity::toDomain)
}

private fun NewOrderRequest.fingerprint(): String {
    val canonical = listOf(itemName.trim(), quantity.toString(), customerName.trim()).joinToString("\u001F")
    return MessageDigest.getInstance("SHA-256")
        .digest(canonical.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun OrderOperationEntity.toDomain(): StoredOrderOperation = StoredOrderOperation(
    operationId = OperationId(operationId),
    idempotencyKey = IdempotencyKey(idempotencyKey),
    state = DurableOperationState.valueOf(state),
    attemptsUsed = attemptsUsed,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    order = if (orderId != null && orderItem != null && orderTotal != null) {
        Order(orderId, orderItem, orderTotal)
    } else null,
    failureCode = failureCode,
)
