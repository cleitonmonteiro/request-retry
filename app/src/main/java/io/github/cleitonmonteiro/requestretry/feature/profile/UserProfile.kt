package io.github.cleitonmonteiro.requestretry.feature.profile

data class UserProfile(
    val name: String,
    val email: String,
)

val sampleProfile = UserProfile(name = "Ada Lovelace", email = "ada@example.com")
