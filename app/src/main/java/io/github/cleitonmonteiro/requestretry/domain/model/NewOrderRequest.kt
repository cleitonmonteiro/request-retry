package io.github.cleitonmonteiro.requestretry.domain.model

data class NewOrderRequest(
    val itemName: String,
    val quantity: Int,
    val customerName: String,
)
