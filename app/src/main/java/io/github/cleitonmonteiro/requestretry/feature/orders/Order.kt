package io.github.cleitonmonteiro.requestretry.feature.orders

data class Order(
    val id: String,
    val item: String,
    val total: Double,
)

val sampleOrders = listOf(
    Order(id = "A-1001", item = "Mechanical keyboard", total = 89.90),
    Order(id = "A-1002", item = "USB-C hub", total = 34.50),
    Order(id = "A-1003", item = "Monitor arm", total = 129.00),
)
