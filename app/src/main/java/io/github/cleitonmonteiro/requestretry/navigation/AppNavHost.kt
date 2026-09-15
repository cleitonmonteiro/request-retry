package io.github.cleitonmonteiro.requestretry.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.cleitonmonteiro.requestretry.feature.home.HomeScreen
import io.github.cleitonmonteiro.requestretry.feature.orders.OrdersRoute
import io.github.cleitonmonteiro.requestretry.feature.picker.PickerRoute
import io.github.cleitonmonteiro.requestretry.feature.profile.ProfileRoute

private object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val ORDERS = "orders"
    const val PICKER = "picker"
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
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenOrders = { navController.navigate(Routes.ORDERS) },
                onOpenPicker = { navController.navigate(Routes.PICKER) },
            )
        }
        // Each ViewModel is scoped to this NavBackStackEntry via hiltViewModel(), so leaving
        // and re-entering a demo always starts with a fresh retry budget.
        composable(Routes.PROFILE) {
            ProfileRoute(onLeave = { navController.popBackStack() })
        }
        composable(Routes.ORDERS) {
            OrdersRoute(onLeave = { navController.popBackStack() })
        }
        composable(Routes.PICKER) {
            PickerRoute(onLeave = { navController.popBackStack() })
        }
    }
}
