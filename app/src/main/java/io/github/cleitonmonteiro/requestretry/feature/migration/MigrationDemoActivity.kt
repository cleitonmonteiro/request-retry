package io.github.cleitonmonteiro.requestretry.feature.migration

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import io.github.cleitonmonteiro.requestretry.domain.model.Action
import io.github.cleitonmonteiro.requestretry.ui.action.ActionButton
import io.github.cleitonmonteiro.requestretry.ui.action.LocalActionHandler
import io.github.cleitonmonteiro.requestretry.ui.action.rememberActionHandler
import io.github.cleitonmonteiro.requestretry.ui.components.AppTopBar
import io.github.cleitonmonteiro.requestretry.ui.theme.RequestRetryTheme

/**
 * Stand-in for a screen reached from a legacy, non-Compose part of the app during an incremental
 * migration — its own [setContent], not a destination inside `AppNavHost`'s `NavHost`.
 */
@AndroidEntryPoint
class MigrationDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RequestRetryTheme {
                MigrationDemoScreen(onClose = { finish() })
            }
        }
    }
}

/**
 * This screen is NOT nested under AppNavHost's NavHost, so it can't rely on the
 * CompositionLocalProvider AppNavHost sets up — CompositionLocal only reaches its own
 * composition subtree. It provides its own [ActionHandler][io.github.cleitonmonteiro.requestretry.ui.action.ActionHandler]
 * instead, right at its own root. `navController = null` is what makes a [Action.Deeplink]
 * correctly skip in-app resolution (there's no Compose nav graph to check against here) and
 * fall straight through to a real intent, same as tapping the link outside the app.
 */
@Composable
private fun MigrationDemoScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val handler = rememberActionHandler(navController = null, onClose = onClose)
    CompositionLocalProvider(LocalActionHandler provides handler) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                AppTopBar(title = "Legacy migration demo", canNavigateBack = true, onNavigateBack = onClose)
            },
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
                Text("This screen is hosted from its own Activity, outside AppNavHost's NavHost.")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Each action below still works — this screen just provides its own ActionHandler.")
                Spacer(modifier = Modifier.height(24.dp))
                // Stand-in for a server response; a real screen would get this from a ViewModel,
                // same as every other feature/ screen.
                ActionButton(Action.ExternalLink(url = "https://example.com/track/I-2", label = "Track shipment"))
                Spacer(modifier = Modifier.height(12.dp))
                ActionButton(Action.Deeplink(uri = "requestretry://orders", label = "View orders (falls back to intent)"))
                Spacer(modifier = Modifier.height(12.dp))
                ActionButton(Action.Close(label = "Close"))
            }
        }
    }
}
