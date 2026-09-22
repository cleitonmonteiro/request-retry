package io.github.cleitonmonteiro.requestretry.domain.usecase

import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile
import io.github.cleitonmonteiro.requestretry.domain.repository.ProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** Retrieves the profile as a cold stream so execution remains owned by the caller. */
class GetProfileUseCase @Inject constructor(
    private val repository: ProfileRepository,
) {
    operator fun invoke(): Flow<UserProfile> = repository.getProfile()
}
