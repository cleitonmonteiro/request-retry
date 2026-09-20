package io.github.cleitonmonteiro.requestretry.domain.model

data class NewOrderRequest(
    val itemName: String,
    val quantity: Int,
    val customerName: String,
    /** Stable across retries of one submission; a new submission must use a new key. */
    val idempotencyKey: String = "",
)
