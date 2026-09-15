package io.github.cleitonmonteiro.requestretry.data.remote

import javax.inject.Inject

class OrdersRemoteDataSource @Inject constructor(
    private val network: FakeNetwork,
) {
    suspend fun fetchOrders(): List<OrderDto> = network.execute { sampleOrderDtos }
}
