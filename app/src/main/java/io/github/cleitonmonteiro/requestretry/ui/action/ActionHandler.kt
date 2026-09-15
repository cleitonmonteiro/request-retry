package io.github.cleitonmonteiro.requestretry.ui.action

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import androidx.navigation.NavController
import io.github.cleitonmonteiro.requestretry.domain.model.Action

/** Executes an [Action] — the single place that knows what a deeplink/external link/close actually does. */
fun interface ActionHandler {
    fun handle(action: Action)
}

/** No-op default so [ActionButton] renders in previews and tests without a provider. */
val LocalActionHandler = staticCompositionLocalOf<ActionHandler> { ActionHandler {} }

/**
 * Builds the app's one [ActionHandler], provided once above the NavHost via [LocalActionHandler]
 * so any composable at any depth can execute a server-driven [Action] without the destination
 * threading a callback down for it.
 */
@Composable
fun rememberActionHandler(navController: NavController, onClose: () -> Unit): ActionHandler {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    return remember(navController, onClose) {
        ActionHandler { action ->
            when (action) {
                is Action.Deeplink -> handleDeeplink(action.uri, navController, context)
                is Action.ExternalLink -> handleExternalLink(action.url, uriHandler, context)
                is Action.Close -> onClose()
                Action.Unknown -> Unit
            }
        }
    }
}

/**
 * Tries in-app navigation first — the whole point of a deeplink is that [navController] can
 * resolve it against a destination's `navDeepLink` pattern without this handler ever naming a
 * route (`Routes` in `AppNavHost.kt` is private). Falls through to a real intent, then a toast,
 * for a deeplink no destination declares — the same fallback chain a browser would use.
 */
private fun handleDeeplink(uri: String, navController: NavController, context: Context) {
    val navigated = runCatching { navController.navigate(uri.toUri()) }.isSuccess
    if (!navigated) openExternally(uri, context)
}

private fun handleExternalLink(url: String, uriHandler: UriHandler, context: Context) {
    val opened = runCatching { uriHandler.openUri(url) }.isSuccess
    if (!opened) showUnhandledToast(context, url)
}

private fun openExternally(target: String, context: Context) {
    val opened = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
    }.isSuccess
    if (!opened) showUnhandledToast(context, target)
}

/** No app on this device declares an intent-filter for the target — real deeplink handling always needs this fallback. */
private fun showUnhandledToast(context: Context, target: String) {
    Toast.makeText(context, "No app can handle $target", Toast.LENGTH_SHORT).show()
}
