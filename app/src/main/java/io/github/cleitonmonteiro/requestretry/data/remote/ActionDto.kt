package io.github.cleitonmonteiro.requestretry.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire-shaped enum: kotlinx.serialization matches JSON strings to enum constants by name unless
 * told otherwise, so [SerialName] on each entry documents the server's actual (lowercase,
 * snake_case) values without forcing the Kotlin side to use non-idiomatic constant names.
 * [UNKNOWN] is the deliberate exception to "surface a mismatch instead of hiding it": a shipped
 * client can't be updated retroactively, so a *future* action type the server adds must degrade
 * gracefully here rather than throw. `NetworkModule`'s `Json { coerceInputValues = true }` is
 * what routes an unrecognized wire string to this default instead of failing to decode.
 */
@Serializable
enum class ActionTypeDto {
    @SerialName("deeplink") DEEPLINK,
    @SerialName("external_link") EXTERNAL_LINK,
    @SerialName("close") CLOSE,
    UNKNOWN,
}

@Serializable
data class ActionDto(
    @SerialName("action_type") val actionType: ActionTypeDto = ActionTypeDto.UNKNOWN,
    @SerialName("target") val target: String? = null,
    @SerialName("label") val label: String? = null,
)
