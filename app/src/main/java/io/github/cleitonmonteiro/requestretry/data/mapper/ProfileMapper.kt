package io.github.cleitonmonteiro.requestretry.data.mapper

import io.github.cleitonmonteiro.requestretry.data.remote.ProfileDto
import io.github.cleitonmonteiro.requestretry.domain.model.UserProfile

fun ProfileDto.toDomain() = UserProfile(name = full_name, email = email_address)
