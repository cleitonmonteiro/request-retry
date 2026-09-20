package io.github.cleitonmonteiro.requestretry.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

class OrdersRemoteDataSource @Inject constructor(
    private val httpClient: HttpClient,
    private val apiClient: ApiClient,
) {
    suspend fun fetchOrders(): List<OrderDto> = apiClient.execute {
        httpClient.get("$BASE_URL/orders").body()
    }

    suspend fun createOrder(request: NewOrderRequestDto): OrderDto = apiClient.execute {
        httpClient.post("$BASE_URL/orders") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
