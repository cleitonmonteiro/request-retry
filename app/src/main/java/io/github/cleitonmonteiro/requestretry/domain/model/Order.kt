package io.github.cleitonmonteiro.requestretry.domain.model

data class Order(
    val id: String,
    val item: String,
    val total: Double,
)
