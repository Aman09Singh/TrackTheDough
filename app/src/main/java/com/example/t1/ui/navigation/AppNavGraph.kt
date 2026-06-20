package com.example.t1.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.t1.ui.analytics.AnalyticsScreen
import com.example.t1.ui.home.HomeScreen
import com.example.t1.ui.permissions.PermissionScreen
import com.example.t1.ui.transactions.TransactionDetailScreen
import com.example.t1.ui.transactions.TransactionListScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.HOME,
        modifier = modifier,
    ) {
        composable(AppRoute.PERMISSIONS) {
            PermissionScreen(onPermissionsGranted = {
                navController.navigate(AppRoute.HOME) {
                    popUpTo(AppRoute.PERMISSIONS) { inclusive = true }
                }
            })
        }
        composable(AppRoute.HOME) {
            HomeScreen(onTransactionClick = { id ->
                navController.navigate(AppRoute.transactionDetail(id))
            })
        }
        composable(AppRoute.TRANSACTION_LIST) {
            TransactionListScreen(onTransactionClick = { id ->
                navController.navigate(AppRoute.transactionDetail(id))
            })
        }
        composable(AppRoute.ANALYTICS) {
            AnalyticsScreen()
        }
        composable(
            route = AppRoute.TRANSACTION_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStack ->
            TransactionDetailScreen(
                transactionId = backStack.arguments!!.getLong("id"),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
