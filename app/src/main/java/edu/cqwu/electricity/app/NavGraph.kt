package edu.cqwu.electricity.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import edu.cqwu.electricity.accountmanagerv2.accountManagerGraph
import edu.cqwu.electricity.campusnetwork.campusNetworkGraph
import edu.cqwu.electricity.cardcenter.cardCenterGraph
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.electricity.electricityGraph
import edu.cqwu.electricity.feedback.feedbackGraph
import edu.cqwu.electricity.feeservicehall.feeServiceHallGraph
import edu.cqwu.electricity.jwxt.jwxtGraph
import edu.cqwu.electricity.login.loginGraph
import edu.cqwu.electricity.login.ui.LoginScreen
import edu.cqwu.electricity.notice.noticeGraph
import edu.cqwu.electricity.notice.ui.NoticeViewModel
import edu.cqwu.electricity.person.personGraph
import edu.cqwu.electricity.profile.profileGraph
import edu.cqwu.electricity.qrcode.qrcodeGraph
import edu.cqwu.electricity.scan.scanGraph
import edu.cqwu.electricity.settings.settingsGraph
import edu.cqwu.electricity.shortcut.shortcutGraph
import edu.cqwu.electricity.speakup.speakUpGraph
import edu.cqwu.electricity.webview.ui.LocalWebViewReloadAfterLogin
import edu.cqwu.electricity.webview.ui.LocalWebViewReloadConsumed
import edu.cqwu.electricity.webview.ui.UnifiedWebViewScreen

/**
 * 全局导航图的**装配层**。
 *
 * 各模块的页面路由都放在自己模块根目录的 `XxxNavigation.kt`（`NavGraphBuilder.xxxGraph(...)`），
 * 这里只做三件事：
 * 1. 创建跨模块共享的状态（通知 ViewModel、WebView 登录后刷新标记）；
 * 2. 注册**公共路由**（主页 Tab 容器、通用内置浏览器、WebView 专用登录）；
 * 3. 逐行调用各模块的 graph 函数。
 *
 * 因此**新增页面不需要动这个文件**——只改对应模块的 `XxxNavigation.kt`；只有新增一个模块时，
 * 才在这里加一行 `xxxGraph(navController, appSettings)` 和一条 import。
 *
 * 路由常量与拼装函数见 [Routes]（`app/Routes.kt`）。
 */

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // 通知列表页与详情页共用同一个实例（详情返回时靠它触发列表刷新），所以在这里创建后传给 noticeGraph
    val noticeViewModel: NoticeViewModel = viewModel()
    var webViewReloadAfterLogin by rememberSaveable { mutableStateOf(false) }
    val appSettings = LocalAppSettingsState.current

    CompositionLocalProvider(
        LocalWebViewReloadAfterLogin provides webViewReloadAfterLogin,
        LocalWebViewReloadConsumed provides { webViewReloadAfterLogin = false },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.MAIN_TABS,
            modifier = modifier,
        ) {
            // ── 公共路由 ──

            // 主页 Tab 容器（HorizontalPager：首页 + 我的）
            animatedComposable(settings = appSettings, route = Routes.MAIN_TABS) {
                MainTabScreen()
            }

            // 通用内置浏览器
            animatedComposable(
                settings = appSettings,
                route = Routes.UNIFIED_WEBVIEW,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val url = android.net.Uri.decode(backStackEntry.arguments?.getString("url") ?: "")
                val title = android.net.Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
                UnifiedWebViewScreen(
                    url = url,
                    initialTitle = title.ifBlank { "" },
                    onClose = { navController.popBackStack() },
                    reloadAfterLogin = webViewReloadAfterLogin,
                    onReloadConsumed = { webViewReloadAfterLogin = false },
                )
            }

            // WebView 专用本地登录：返回时只退回 WebView，登录成功后刷新 WebView
            animatedComposable(settings = appSettings, route = Routes.WEBVIEW_LOGIN) {
                LoginScreen(
                    onBack = { navController.popBackStack() },
                    onLoginSuccess = {
                        webViewReloadAfterLogin = true
                        navController.popBackStack()
                    },
                )
            }

            // ── 各模块的路由表（新增页面只改对应模块的 XxxNavigation.kt）──

            accountManagerGraph(navController, appSettings)
            campusNetworkGraph(navController, appSettings)
            cardCenterGraph(navController, appSettings)
            electricityGraph(navController, appSettings)
            feeServiceHallGraph(navController, appSettings)
            feedbackGraph(navController, appSettings)
            jwxtGraph(navController, appSettings)
            loginGraph(navController, appSettings)
            noticeGraph(navController, appSettings, noticeViewModel)
            personGraph(navController, appSettings)
            profileGraph(navController, appSettings)
            qrcodeGraph(navController, appSettings)
            scanGraph(navController, appSettings)
            settingsGraph(navController, appSettings)
            shortcutGraph(navController, appSettings)
            speakUpGraph(navController, appSettings)
        }
    }
}
