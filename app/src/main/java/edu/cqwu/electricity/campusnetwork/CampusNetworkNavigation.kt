package edu.cqwu.electricity.campusnetwork

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.campusnetwork.accessor.ui.AccessorScreen
import edu.cqwu.electricity.campusnetwork.accessor.ui.AccessorViewModel
import edu.cqwu.electricity.campusnetwork.portal.ui.PortalServiceScreen
import edu.cqwu.electricity.campusnetwork.speedtest.ui.SpeedTestScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.campusNetworkGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 校园网络（入口页）
    animatedComposable(settings = settings, route = Routes.CAMPUS_NETWORK) {
        CampusNetworkScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 校园网络 — 接入者信息
    animatedComposable(settings = settings, route = Routes.CAMPUS_NETWORK_ACCESSOR_INFO) {
        // viewModel() 在此处调用，作用域绑定到本路由条目，离开页面即销毁
        val accessorViewModel: AccessorViewModel = viewModel()
        AccessorScreen(
            viewModel = accessorViewModel,
            onBack = { navController.popBackStack() },
        )
    }

    // 校园网络 — 校内网络测速
    animatedComposable(settings = settings, route = Routes.CAMPUS_NETWORK_SPEED_TEST) {
        SpeedTestScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 校园网络 — 网络服务（认证网关会话，本地化）
    animatedComposable(settings = settings, route = Routes.CAMPUS_NETWORK_PORTAL_SERVICE) {
        PortalServiceScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
