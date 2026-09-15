package io.github.cleitonmonteiro.requestretry.data.remote

import javax.inject.Inject

class ProfileRemoteDataSource @Inject constructor(
    private val network: FakeNetwork,
) {
    suspend fun fetchProfile(): ProfileDto = network.execute { sampleProfileDto }
}
