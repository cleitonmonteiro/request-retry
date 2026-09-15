package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire-shaped: [SerialName] documents the snake_case keys the catalog endpoint sends. */
@Serializable
data class ItemDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String,
)

/**
 * The send-item endpoint's confirmation payload: it echoes the submitted id back, plus an
 * SDUI-style [action] telling the client what to do next.
 */
@Serializable
data class SendItemResponseDto(
    @SerialName("item_id") val itemId: String,
    @SerialName("action") val action: ActionDto,
)
