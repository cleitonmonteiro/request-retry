package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
/** Wire representation of the server's status for a submitted order operation. */
data class OrderOperationStatusDto(
    @SerialName("operation_id") val operationId: String,
    val status: String,
    val order: OrderDto? = null,
    @SerialName("error_code") val errorCode: String? = null,
)
