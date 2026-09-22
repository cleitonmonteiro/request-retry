package io.github.cleitonmonteiro.requestretry.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.cleitonmonteiro.requestretry.feature.createorder.CreateOrderRoute
import io.github.cleitonmonteiro.requestretry.feature.home.HomeIntent
import io.github.cleitonmonteiro.requestretry.feature.home.HomeScreen
import io.github.cleitonmonteiro.requestretry.feature.profile.ProfileRoute

/** Internal route names used by the app's small navigation graph. */
internal object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val CREATE_ORDER = "create_order"
}

internal fun routeTitle(route: String?): String = when (route) {
    Routes.PROFILE -> "Profile demo"
    Routes.CREATE_ORDER -> "Create order demo"
    else -> "Request Retry"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onIntent = { intent ->
                    when (intent) {
                        HomeIntent.OpenProfile -> navController.navigate(Routes.PROFILE)
                        HomeIntent.OpenCreateOrder -> navController.navigate(Routes.CREATE_ORDER)
                    }
                },
            )
        }
        composable(Routes.PROFILE) {
            ProfileRoute(onLeave = { navController.popBackStack() })
        }
        composable(Routes.CREATE_ORDER) {
            CreateOrderRoute(onLeave = { navController.popBackStack() })
        }
    }
}
