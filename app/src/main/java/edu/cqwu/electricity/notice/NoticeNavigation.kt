package edu.cqwu.electricity.notice

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.notice.ui.NoticeDetailScreen
import edu.cqwu.electricity.notice.ui.NoticePreviewHolder
import edu.cqwu.electricity.notice.ui.NoticeScreen
import edu.cqwu.electricity.notice.ui.NoticeViewModel
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 *
 * 列表页的 [NoticeViewModel] 在**列表目的地内**用 `viewModel()` 创建，作用域就是这一页的
 * `NavBackStackEntry`：出栈即销毁，从首页再次进入必然是全新实例并重新加载——这是 Navigation
 * 的默认行为，也是通知页与账单页等页面表现一致的原因。**不要**再把它提升到 `AppNavGraph` 里共享，
 * 否则「出栈即销毁」失效，就得靠标志位与返回键拦截去手动模拟。
 *
 * 详情页需要的「用户点的是哪一条」由 [NoticePreviewHolder] 传递（它在 `AppNavGraph` 中创建），
 * 这样列表数据仍然跟着列表页的生命周期走。
 */
internal fun NavGraphBuilder.noticeGraph(
    navController: NavHostController,
    settings: AppSettingsState,
    previewHolder: NoticePreviewHolder,
) {
    // 通知公告
    animatedComposable(settings = settings, route = Routes.NOTICE) {
        val noticeViewModel: NoticeViewModel = viewModel()
        NoticeScreen(
            viewModel = noticeViewModel,
            onBack = { navController.popBackStack() },
            onNavigateToNoticeDetail = { notice ->
                previewHolder.select(notice)
                navController.navigate(Routes.noticeDetailRoute(notice.wid))
            },
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
            preview = previewHolder.previewFor(wid),
            onBack = { navController.popBackStack() },
            onOpenInBrowser = { url, title ->
                navController.navigate(Routes.unifiedWebViewRoute(url, title))
            },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }
}
