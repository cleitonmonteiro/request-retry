package io.github.cleitonmonteiro.requestretry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.cleitonmonteiro.requestretry.navigation.AppNavHost
import io.github.cleitonmonteiro.requestretry.navigation.Routes
import io.github.cleitonmonteiro.requestretry.navigation.routeTitle
import io.github.cleitonmonteiro.requestretry.ui.components.AppTopBar
import io.github.cleitonmonteiro.requestretry.ui.theme.RequestRetryTheme

@AndroidEntryPoint
/** Hosts the Compose navigation graph and the app-wide top bar. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RequestRetryTheme {
                val navController = rememberNavController()
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        AppTopBar(
                            title = routeTitle(currentRoute),
                            canNavigateBack = currentRoute != null && currentRoute != Routes.HOME,
                            onNavigateBack = { navController.popBackStack() },
                        )
                    },
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
