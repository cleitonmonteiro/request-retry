package io.github.cleitonmonteiro.requestretry.domain.repository

import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getProfile(): UserProfile
}
