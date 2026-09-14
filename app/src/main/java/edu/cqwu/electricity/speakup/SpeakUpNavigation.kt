package edu.cqwu.electricity.speakup

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.speakup.ui.MessageDetailScreen
import edu.cqwu.electricity.speakup.ui.MessageListScreen
import edu.cqwu.electricity.speakup.ui.SpeakUpScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.speakUpGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 有话要说 — 咨询区列表
    animatedComposable(settings = settings, route = Routes.SPEAK_UP) {
        SpeakUpScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
            onNavigateToWebView = { url, title ->
                navController.navigate(Routes.unifiedWebViewRoute(url, title))
            },
            onNavigateToMessages = { areaCode, areaName ->
                navController.navigate(Routes.speakUpMessagesRoute(areaCode, areaName))
            },
        )
    }

    // 有话要说 — 留言列表
    animatedComposable(
        settings = settings,
        route = Routes.SPEAK_UP_MESSAGES,
        arguments = listOf(
            navArgument("areaCode") { type = NavType.StringType },
            navArgument("areaName") { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val areaCode = backStackEntry.arguments?.getString("areaCode") ?: ""
        val areaName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("areaName") ?: "", "UTF-8")
        MessageListScreen(
            areaCode = areaCode,
            areaName = areaName,
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
            onMessageClick = { wid ->
                navController.navigate(Routes.speakUpDetailRoute(wid))
            },
        )
    }

    // 有话要说 — 留言详情
    animatedComposable(
        settings = settings,
        route = Routes.SPEAK_UP_DETAIL,
        arguments = listOf(
            navArgument("wid") { type = NavType.StringType },
        ),
    ) { backStackEntry ->
        val wid = backStackEntry.arguments?.getString("wid") ?: ""
        MessageDetailScreen(
            wid = wid,
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }
}
