package io.github.cleitonmonteiro.requestretry.domain.model

data class NewOrderRequest(
    val itemName: String,
    val quantity: Int,
    val customerName: String,
    /** Stable across retries/reconciliation; a new business intention gets a new identity. */
    val operationId: OperationId,
    val idempotencyKey: IdempotencyKey,
)
