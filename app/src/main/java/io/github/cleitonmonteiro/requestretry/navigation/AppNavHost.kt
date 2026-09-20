package io.github.cleitonmonteiro.requestretry.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import io.github.cleitonmonteiro.requestretry.feature.createorder.CreateOrderRoute
import io.github.cleitonmonteiro.requestretry.feature.home.HomeScreen
import io.github.cleitonmonteiro.requestretry.feature.orders.OrdersRoute
import io.github.cleitonmonteiro.requestretry.feature.picker.PickerRoute
import io.github.cleitonmonteiro.requestretry.feature.profile.ProfileRoute
import io.github.cleitonmonteiro.requestretry.ui.action.LocalActionHandler
import io.github.cleitonmonteiro.requestretry.ui.action.rememberActionHandler

internal object Routes {
    const val HOME = "home"
    const val PROFILE = "profile"
    const val ORDERS = "orders"
    const val PICKER = "picker"
    const val CREATE_ORDER = "create_order"
}

/**
 * Display title for [AppTopBar][io.github.cleitonmonteiro.requestretry.ui.components.AppTopBar],
 * keyed by [NavHostController.currentBackStackEntry]'s route — kept next to [Routes] so a new
 * destination can't add one constant without the other.
 */
internal fun routeTitle(route: String?): String = when (route) {
    Routes.PROFILE -> "Profile demo"
    Routes.ORDERS -> "Orders demo"
    Routes.PICKER -> "Item picker demo"
    Routes.CREATE_ORDER -> "Create order demo"
    else -> "Request Retry"
}

/**
 * `requestretry://<route>` deep links, so a server-driven
 * [io.github.cleitonmonteiro.requestretry.domain.model.Action.Deeplink] can route in-app without
 * `ui/action/ActionHandler.kt` ever naming a [Routes] constant — it just hands the URI to
 * [androidx.navigation.NavController.navigate], which resolves it against these patterns.
 */
private const val DEEPLINK_SCHEME = "requestretry"

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val actionHandler = rememberActionHandler(navController, onClose = { navController.popBackStack() })

    CompositionLocalProvider(LocalActionHandler provides actionHandler) {
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
                    onOpenCreateOrder = { navController.navigate(Routes.CREATE_ORDER) },
                )
            }
            // Each ViewModel is scoped to this NavBackStackEntry via hiltViewModel(), so leaving
            // and re-entering a demo always starts with a fresh retry budget.
            composable(
                route = Routes.PROFILE,
                deepLinks = listOf(navDeepLink { uriPattern = "$DEEPLINK_SCHEME://${Routes.PROFILE}" }),
            ) {
                ProfileRoute(onLeave = { navController.popBackStack() })
            }
            composable(
                route = Routes.ORDERS,
                deepLinks = listOf(navDeepLink { uriPattern = "$DEEPLINK_SCHEME://${Routes.ORDERS}" }),
            ) {
                OrdersRoute(onLeave = { navController.popBackStack() })
            }
            composable(
                route = Routes.PICKER,
                deepLinks = listOf(navDeepLink { uriPattern = "$DEEPLINK_SCHEME://${Routes.PICKER}" }),
            ) {
                PickerRoute(onLeave = { navController.popBackStack() })
            }
            composable(
                route = Routes.CREATE_ORDER,
                deepLinks = listOf(navDeepLink { uriPattern = "$DEEPLINK_SCHEME://${Routes.CREATE_ORDER}" }),
            ) {
                CreateOrderRoute(onLeave = { navController.popBackStack() })
            }
        }
    }
}
