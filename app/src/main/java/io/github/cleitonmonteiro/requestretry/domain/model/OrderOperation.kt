package io.github.cleitonmonteiro.requestretry.domain.model

enum class DurableOperationState {
    CREATED,
    SENDING,
    PENDING_CONFIRMATION,
    SUCCEEDED,
    REJECTED,
    OUTCOME_UNKNOWN,
    EXPIRED,
}

data class StoredOrderOperation(
    val operationId: OperationId,
    val idempotencyKey: IdempotencyKey,
    val state: DurableOperationState,
    val attemptsUsed: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val order: Order? = null,
    val failureCode: String? = null,
)

sealed interface OrderOperationStatus {
    data object Processing : OrderOperationStatus
    data class Succeeded(val order: Order) : OrderOperationStatus
    data class Rejected(val code: String?) : OrderOperationStatus
    data object Unknown : OrderOperationStatus
}
