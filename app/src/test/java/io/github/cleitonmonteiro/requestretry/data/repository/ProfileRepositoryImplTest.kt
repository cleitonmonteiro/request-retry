package io.github.cleitonmonteiro.requestretry.data.repository

import io.github.cleitonmonteiro.requestretry.data.remote.ApiClient
import io.github.cleitonmonteiro.requestretry.data.remote.ProfileRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.remote.mockHttpClient
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRepositoryImplTest {

    @Test
    fun `getProfile returns the mapped domain model`() = runTest {
        // Arrange
        val httpClient = mockHttpClient { """{"full_name":"Ada Lovelace","email_address":"ada@example.com"}""" }
        val repository = ProfileRepositoryImpl(ProfileRemoteDataSource(httpClient, ApiClient()))

        // Act
        val profile = repository.getProfile().first()

        // Assert
        assertEquals(UserProfile(name = "Ada Lovelace", email = "ada@example.com"), profile)
    }
}
