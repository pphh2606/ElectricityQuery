package edu.cqwu.electricity.jwxt

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.jwxt.moreservice.JwxtMoreServiceScreen
import edu.cqwu.electricity.jwxt.score.JwxtScoreScreen
import edu.cqwu.electricity.jwxt.ui.JwxtHomeScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.jwxtGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 教务首页（jwfw /jwmobile 本地化页；入口：首页「本科教务系统」「移动教务」）
    animatedComposable(settings = settings, route = Routes.JWXT_HOME) {
        JwxtHomeScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 教务「更多服务」（二级页；入口：教务首页九宫格的「更多服务」格子）
    animatedComposable(settings = settings, route = Routes.JWXT_MORE_SERVICE) {
        JwxtMoreServiceScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 教务「成绩查询」（二级页；入口：教务首页九宫格的「成绩查询」格子）
    animatedComposable(settings = settings, route = Routes.JWXT_SCORE) {
        JwxtScoreScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
