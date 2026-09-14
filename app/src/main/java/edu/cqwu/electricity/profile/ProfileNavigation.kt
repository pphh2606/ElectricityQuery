package edu.cqwu.electricity.profile

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.profile.ui.MyInfoScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.profileGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 我的信息（原生页面）
    animatedComposable(settings = settings, route = Routes.MY_INFO) {
        MyInfoScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
            onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
        )
    }
}
