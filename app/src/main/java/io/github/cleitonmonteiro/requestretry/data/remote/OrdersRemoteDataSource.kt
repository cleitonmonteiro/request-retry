package io.github.cleitonmonteiro.requestretry.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

class OrdersRemoteDataSource @Inject constructor(
    private val httpClient: HttpClient,
    private val apiClient: ApiClient,
) {
    suspend fun fetchOrders(): List<OrderDto> = apiClient.executeHttp {
        httpClient.get("$BASE_URL/orders").body()
    }

    suspend fun createOrder(
        request: NewOrderRequestDto,
        operationId: String,
        idempotencyKey: String,
    ): OrderDto = apiClient.executeHttp {
        httpClient.post("$BASE_URL/orders") {
            contentType(ContentType.Application.Json)
            header("X-Operation-ID", operationId)
            header("Idempotency-Key", idempotencyKey)
            setBody(request)
        }.body()
    }

    suspend fun fetchOperationStatus(operationId: String): OrderOperationStatusDto = apiClient.executeHttp {
        httpClient.get("$BASE_URL/operations/$operationId").body()
    }
}
