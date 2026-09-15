package io.github.cleitonmonteiro.requestretry.data.remote

/** Wire-shaped: a real catalog endpoint would name fields like this. */
data class ItemDto(
    val item_id: String,
    val item_name: String,
)

internal val sampleItemDtos = listOf(
    ItemDto(item_id = "I-1", item_name = "Backpack"),
    ItemDto(item_id = "I-2", item_name = "Water bottle"),
    ItemDto(item_id = "I-3", item_name = "Notebook"),
)
