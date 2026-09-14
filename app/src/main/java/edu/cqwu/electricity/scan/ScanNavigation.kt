package edu.cqwu.electricity.scan

import androidx.compose.ui.platform.LocalResources
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.scan.ui.ScanScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.scanGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 扫码页面
    animatedComposable(settings = settings, route = Routes.SCAN) {
        val resources = LocalResources.current
        ScanScreen(
            onBack = { navController.popBackStack() },
            onOpenUrl = { url ->
                navController.popBackStack()
                navController.navigate(Routes.unifiedWebViewRoute(url, resources.getString(R.string.scan_title)))
            },
        )
    }
}
