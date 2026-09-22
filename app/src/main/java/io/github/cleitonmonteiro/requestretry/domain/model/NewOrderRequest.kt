package io.github.cleitonmonteiro.requestretry.domain.model

/** Immutable command to create an order, including the stable identities used for safe replay. */
data class NewOrderRequest(
    val itemName: String,
    val quantity: Int,
    val customerName: String,
    /** Stable across retries and foreground status verification; a new intention gets a new identity. */
    val operationId: OperationId,
    val idempotencyKey: IdempotencyKey,
)
