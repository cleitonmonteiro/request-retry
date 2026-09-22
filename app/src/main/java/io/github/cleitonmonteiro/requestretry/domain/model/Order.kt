package io.github.cleitonmonteiro.requestretry.domain.model

/** Order returned after the backend has accepted a create-order operation. */
data class Order(
    val id: String,
    val item: String,
    val total: Double,
)
