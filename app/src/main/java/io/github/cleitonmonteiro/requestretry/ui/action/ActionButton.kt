package io.github.cleitonmonteiro.requestretry.ui.action

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.cleitonmonteiro.requestretry.domain.model.Action
import io.github.cleitonmonteiro.requestretry.ui.theme.RequestRetryTheme

/**
 * Renders any server-driven [Action] as a single button, reading [LocalActionHandler] so the
 * caller never needs to know what the action does — this is the reuse payoff: any component
 * shows an action with `ActionButton(action)`, no context/intents/when/threaded callbacks needed.
 */
@Composable
fun ActionButton(action: Action, modifier: Modifier = Modifier) {
    if (action is Action.Unknown) return
    val handler = LocalActionHandler.current
    Button(onClick = { handler.handle(action) }, modifier = modifier) {
        Text(action.label)
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonDeeplinkPreview() {
    RequestRetryTheme {
        ActionButton(Action.Deeplink(uri = "requestretry://orders", label = "View orders"))
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonExternalLinkPreview() {
    RequestRetryTheme {
        ActionButton(Action.ExternalLink(url = "https://example.com/track/I-2", label = "Track shipment"))
    }
}

@Preview(showBackground = true)
@Composable
private fun ActionButtonClosePreview() {
    RequestRetryTheme {
        ActionButton(Action.Close(label = "Done"))
    }
}
