package io.github.cleitonmonteiro.requestretry.data.remote

/** Wire-shaped: fields as a real profile endpoint might name them. */
data class ProfileDto(
    val full_name: String,
    val email_address: String,
)

internal val sampleProfileDto = ProfileDto(full_name = "Ada Lovelace", email_address = "ada@example.com")
