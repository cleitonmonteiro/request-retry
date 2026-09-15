package io.github.cleitonmonteiro.requestretry.data.remote

/** Wire-shaped: amounts travel as strings, as they often do over the wire. */
data class OrderDto(
    val order_id: String,
    val item_name: String,
    val total_amount: String,
)

internal val sampleOrderDtos = listOf(
    OrderDto(order_id = "A-1001", item_name = "Mechanical keyboard", total_amount = "89.90"),
    OrderDto(order_id = "A-1002", item_name = "USB-C hub", total_amount = "34.50"),
    OrderDto(order_id = "A-1003", item_name = "Monitor arm", total_amount = "129.00"),
)
