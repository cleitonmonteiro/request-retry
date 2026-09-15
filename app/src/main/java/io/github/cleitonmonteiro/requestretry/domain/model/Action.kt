package io.github.cleitonmonteiro.requestretry.domain.model

/** What the UI should do once a request completes — an SDUI-style "next action". */
enum class ActionType { DEEPLINK, EXTERNAL_LINK, CLOSE }

/** [target] is a deeplink URI for [ActionType.DEEPLINK], a URL for [ActionType.EXTERNAL_LINK], and null for [ActionType.CLOSE]. */
data class Action(
    val type: ActionType,
    val target: String? = null,
)
