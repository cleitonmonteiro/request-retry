package io.github.cleitonmonteiro.requestretry.data.repository

import io.github.cleitonmonteiro.requestretry.data.remote.FakeNetwork
import io.github.cleitonmonteiro.requestretry.data.remote.ProfileRemoteDataSource
import io.github.cleitonmonteiro.requestretry.data.remote.Scenario
import io.github.cleitonmonteiro.requestretry.data.remote.ScenarioHolder
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRepositoryImplTest {

    @Test
    fun `getProfile returns the mapped domain model`() = runTest {
        // Arrange
        val scenarios = ScenarioHolder().apply { select(Scenario.ALWAYS_SUCCEED) }
        val repository = ProfileRepositoryImpl(ProfileRemoteDataSource(FakeNetwork(scenarios)))

        // Act
        val profile = repository.getProfile()

        // Assert
        assertEquals(UserProfile(name = "Ada Lovelace", email = "ada@example.com"), profile)
    }
}
