package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire-shaped request body for `POST /orders` — the app's first request with a JSON body. */
@Serializable
data class NewOrderRequestDto(
    @SerialName("item_name") val itemName: String,
    @SerialName("quantity") val quantity: Int,
    @SerialName("customer_name") val customerName: String,
)