package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire-shaped: [SerialName] documents the snake_case keys the profile endpoint sends. */
@Serializable
data class ProfileDto(
    @SerialName("full_name") val fullName: String,
    @SerialName("email_address") val emailAddress: String,
)
