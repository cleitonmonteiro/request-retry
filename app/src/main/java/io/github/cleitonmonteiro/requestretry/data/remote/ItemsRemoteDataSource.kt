package io.github.cleitonmonteiro.requestretry.data.remote

import javax.inject.Inject

class ItemsRemoteDataSource @Inject constructor(
    private val network: FakeNetwork,
) {
    suspend fun fetchItems(): List<ItemDto> = network.execute { sampleItemDtos }

    /** Stands in for a real submission response — echoes the id back as a confirmation. */
    suspend fun submitItem(itemId: String): String = network.execute { itemId }
}
