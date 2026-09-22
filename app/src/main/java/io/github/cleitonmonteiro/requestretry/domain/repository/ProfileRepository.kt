package io.github.cleitonmonteiro.requestretry.domain.repository

import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** Domain contract for retrieving the current user's profile. */
interface ProfileRepository {
    fun getProfile(): Flow<UserProfile>
}
