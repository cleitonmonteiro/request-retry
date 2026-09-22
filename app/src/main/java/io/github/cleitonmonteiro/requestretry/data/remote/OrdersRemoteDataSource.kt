package io.github.cleitonmonteiro.requestretry.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

/** Performs the order endpoints while leaving mapping and retry policy to higher layers. */
class OrdersRemoteDataSource @Inject constructor(
    private val httpClient: HttpClient,
    private val apiClient: ApiClient,
) {
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
}
