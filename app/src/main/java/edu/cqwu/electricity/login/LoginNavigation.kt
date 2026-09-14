package edu.cqwu.electricity.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.app.exitAnim
import edu.cqwu.electricity.login.ui.LoginScreen
import edu.cqwu.electricity.login.ui.QrLoginScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 *
 * 注：从内置浏览器进入的登录页（`Routes.WEBVIEW_LOGIN`）与 WebView 的"登录后刷新"状态强耦合，
 * 属于 app 层公共路由，留在 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.loginGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 本地登录页面（通用入口；可选 accountId 参数预填指定账号条目，为空时自动填充当前激活条目）
    animatedComposable(
        settings = settings,
        route = Routes.LOGIN,
        arguments = listOf(
            navArgument("accountId") {
                type = NavType.StringType
                defaultValue = ""
            }
        ),
    ) { backStackEntry ->
        val initialAccountId = backStackEntry.arguments?.getString("accountId")
            ?.takeIf { it.isNotBlank() }
        LoginScreen(
            initialAccountId = initialAccountId,
            onBack = { navController.popBackStack() },
        )
    }

    // 添加新账号（空白表单）
    animatedComposable(settings = settings, route = Routes.NEW_ACCOUNT_LOGIN) {
        LoginScreen(
            clearForm = true,
            onBack = { navController.popBackStack() },
        )
    }

    // Cookie 过期自动跳转登录（从下往上覆盖 / 从上往下退出，由快变慢；本页转场与其它页不同，
    // 所以不用 animatedComposable，自己声明 enter/exit）
    composable(
        route = Routes.COOKIE_EXPIRED_LOGIN,
        enterTransition = {
            slideInVertically(
                animationSpec = tween(800, easing = LinearEasing),
                initialOffsetY = { it }
            ) + fadeIn(tween(200))
        },
        exitTransition = exitAnim(settings),
        popExitTransition = exitAnim(settings),
    ) {
        LoginScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 扫码登录页面
    animatedComposable(settings = settings, route = Routes.QR_LOGIN) {
        QrLoginScreen(
            onBack = { navController.popBackStack() },
            onLoginSuccess = { navController.popBackStack() },
            onNavigateToQrCodeSettings = { navController.navigate(Routes.QR_CODE_SETTINGS) },
        )
    }
}
