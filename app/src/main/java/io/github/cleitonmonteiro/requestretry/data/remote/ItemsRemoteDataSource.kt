package io.github.cleitonmonteiro.requestretry.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import javax.inject.Inject

class ItemsRemoteDataSource @Inject constructor(
    private val httpClient: HttpClient,
    private val apiClient: ApiClient,
) {
    suspend fun fetchItems(): List<ItemDto> = apiClient.execute {
        httpClient.get("$BASE_URL/items").body()
    }

    suspend fun submitItem(itemId: String): SendItemResponseDto = apiClient.execute {
        httpClient.post("$BASE_URL/items/$itemId/send").body()
    }
}
