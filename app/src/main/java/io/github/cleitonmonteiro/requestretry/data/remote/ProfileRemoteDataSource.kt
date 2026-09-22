package io.github.cleitonmonteiro.requestretry.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject

/** Performs the profile endpoint and returns its wire-shaped response. */
class ProfileRemoteDataSource @Inject constructor(
    private val httpClient: HttpClient,
    private val apiClient: ApiClient,
) {
    suspend fun fetchProfile(): ProfileDto = apiClient.executeHttp {
        httpClient.get("$BASE_URL/profile").body()
    }
}
