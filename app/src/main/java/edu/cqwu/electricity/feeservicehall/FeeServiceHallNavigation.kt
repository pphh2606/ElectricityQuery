package edu.cqwu.electricity.feeservicehall

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.feeservicehall.ui.FeeServiceHallScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.feeServiceHallGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 缴费服务大厅
    animatedComposable(settings = settings, route = Routes.FEE_SERVICE_HALL) {
        FeeServiceHallScreen(
            onBack = { navController.popBackStack() },
            onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }

    // 缴费服务大厅 — 订单 tab
    animatedComposable(settings = settings, route = Routes.FEE_SERVICE_HALL_ORDERS) {
        FeeServiceHallScreen(
            onBack = { navController.popBackStack() },
            onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
            initialTab = 1,
        )
    }
}
