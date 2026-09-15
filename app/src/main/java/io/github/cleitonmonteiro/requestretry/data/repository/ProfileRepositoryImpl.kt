package io.github.cleitonmonteiro.requestretry.data.repository

import io.github.cleitonmonteiro.requestretry.data.mapper.toDomain
import io.github.cleitonmonteiro.requestretry.data.remote.ProfileRemoteDataSource
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.repository.ProfileRepository
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val remote: ProfileRemoteDataSource,
) : ProfileRepository {
    override suspend fun getProfile(): UserProfile = remote.fetchProfile().toDomain()
}
