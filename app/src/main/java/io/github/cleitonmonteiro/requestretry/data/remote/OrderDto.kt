package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-shaped: [SerialName] documents the snake_case keys the orders endpoint sends.
 * [totalAmount] travels as a string, as amounts often do over the wire.
 */
@Serializable
data class OrderDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("total_amount") val totalAmount: String,
)
