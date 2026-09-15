package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-shaped enum: kotlinx.serialization matches JSON strings to enum constants by name unless
 * told otherwise, so [SerialName] on each entry documents the server's actual (lowercase,
 * snake_case) values without forcing the Kotlin side to use non-idiomatic constant names. An
 * unrecognized wire value throws during decoding — there's no `UNKNOWN` fallback here, since this
 * app controls both ends of the wire and a fallback would hide that mismatch instead of surfacing it.
 */
@Serializable
enum class ActionTypeDto {
    @SerialName("deeplink") DEEPLINK,
    @SerialName("external_link") EXTERNAL_LINK,
    @SerialName("close") CLOSE,
}

@Serializable
data class ActionDto(
    @SerialName("action_type") val actionType: ActionTypeDto,
    @SerialName("target") val target: String? = null,
)
