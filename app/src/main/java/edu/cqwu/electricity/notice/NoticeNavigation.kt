package edu.cqwu.electricity.notice

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.notice.ui.NoticeDetailScreen
import edu.cqwu.electricity.notice.ui.NoticeScreen
import edu.cqwu.electricity.notice.ui.NoticeViewModel
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 *
 * [noticeViewModel] 由 `AppNavGraph` 创建后传入：列表页与详情页**共用同一个实例**
 * （详情返回时靠它置 `listRefreshEnabled` 触发列表刷新），不能在各自页面上分别 `viewModel()`。
 */
internal fun NavGraphBuilder.noticeGraph(
    navController: NavHostController,
    settings: AppSettingsState,
    noticeViewModel: NoticeViewModel,
) {
    // 通知公告
    animatedComposable(settings = settings, route = Routes.NOTICE) {
        NoticeScreen(
            viewModel = noticeViewModel,
            onBack = { noticeViewModel.listRefreshEnabled = true; navController.popBackStack() },
            onNavigateToNoticeDetail = { wid -> navController.navigate(Routes.noticeDetailRoute(wid)) },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }

    // 通知公告详情
    animatedComposable(
        settings = settings,
        route = Routes.NOTICE_DETAIL,
        arguments = listOf(navArgument("wid") { type = NavType.StringType }),
    ) { backStackEntry ->
        val wid = backStackEntry.arguments?.getString("wid") ?: ""
        NoticeDetailScreen(
            wid = wid,
            onBack = { navController.popBackStack() },
            onOpenInBrowser = { url, title ->
                navController.navigate(Routes.unifiedWebViewRoute(url, title))
            },
            viewModel = noticeViewModel,
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }
}
