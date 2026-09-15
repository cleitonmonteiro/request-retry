package io.github.cleitonmonteiro.requestretry.domain.model

/**
 * What the UI should do once a request completes — an SDUI-style "next action". Each case
 * carries exactly the payload it needs (no shared nullable `target` field), and [label] is the
 * server-supplied button text, so a generic renderer never needs to know what an action does.
 */
sealed interface Action {
    val label: String

    data class Deeplink(val uri: String, override val label: String) : Action
    data class ExternalLink(val url: String, override val label: String) : Action
    data class Close(override val label: String) : Action

    /** An action type this build doesn't recognize — a newer server added one. Renders as nothing. */
    data object Unknown : Action {
        override val label: String get() = ""
    }
}
