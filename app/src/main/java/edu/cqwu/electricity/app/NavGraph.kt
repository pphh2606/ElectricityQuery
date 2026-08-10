package edu.cqwu.electricity.app

import android.os.Build
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import edu.cqwu.electricity.R
import edu.cqwu.electricity.cardcenter.ui.AccountInfoScreen
import edu.cqwu.electricity.cardcenter.ui.BillScreen
import edu.cqwu.electricity.cardcenter.ui.BillViewModel
import edu.cqwu.electricity.cardcenter.ui.CardCenterScreen
import edu.cqwu.electricity.cardcenter.ui.CardLostScreen
import edu.cqwu.electricity.cardcenter.ui.CardPaymentScreen
import edu.cqwu.electricity.cardcenter.ui.CardRechargeScreen
import edu.cqwu.electricity.cardcenter.ui.CardRechargeViewModel
import edu.cqwu.electricity.electricity.data.DetailType
import edu.cqwu.electricity.electricity.ui.DetailScreen
import edu.cqwu.electricity.electricity.ui.DetailViewModel
import edu.cqwu.electricity.electricity.ui.ElectricityMainScreen
import edu.cqwu.electricity.electricity.ui.ElectricityViewModel
import edu.cqwu.electricity.electricity.ui.MyRoomViewModel
import edu.cqwu.electricity.electricity.ui.PaymentSelectionScreen
import edu.cqwu.electricity.electricity.ui.RechargeRecordScreen
import edu.cqwu.electricity.electricity.ui.RechargeViewModel
import edu.cqwu.electricity.feedback.ui.FeedbackScreen
import edu.cqwu.electricity.feeservicehall.ui.FeeServiceHallScreen
import edu.cqwu.electricity.login.data.SessionManager
import edu.cqwu.electricity.login.data.SessionValidationResult
import edu.cqwu.electricity.login.data.UserCookieStore
import edu.cqwu.electricity.login.ui.LoginScreen
import edu.cqwu.electricity.login.ui.QrLoginScreen
import edu.cqwu.electricity.notice.ui.NoticeDetailScreen
import edu.cqwu.electricity.notice.ui.NoticeScreen
import edu.cqwu.electricity.notice.ui.NoticeViewModel
import edu.cqwu.electricity.profile.ui.MyInfoScreen
import edu.cqwu.electricity.qrcode.data.QrCodeType
import edu.cqwu.electricity.qrcode.ui.QrCodeDisplayScreen
import edu.cqwu.electricity.scan.ui.ScanScreen
import edu.cqwu.electricity.settings.data.PageTransition
import edu.cqwu.electricity.settings.data.ReduceMotion
import edu.cqwu.electricity.settings.ui.AboutScreen
import edu.cqwu.electricity.settings.ui.ConfigScreen
import edu.cqwu.electricity.settings.ui.PersonalizationScreen
import edu.cqwu.electricity.settings.ui.QrCodeSettingsScreen
import edu.cqwu.electricity.settings.ui.SettingsScreen
import edu.cqwu.electricity.settings.ui.StorageClearScreen
import edu.cqwu.electricity.settings.ui.UserAgentEditScreen
import edu.cqwu.electricity.settings.ui.UserAgentSettingsScreen
import edu.cqwu.electricity.settings.ui.WebVpnSettingsScreen
import edu.cqwu.electricity.shortcut.ui.AddShortcutScreen
import edu.cqwu.electricity.speakup.ui.MessageDetailScreen
import edu.cqwu.electricity.speakup.ui.MessageListScreen
import edu.cqwu.electricity.speakup.ui.SpeakUpScreen
import edu.cqwu.electricity.theme.ui.AnimationSettings
import edu.cqwu.electricity.theme.ui.LocalAnimationSettings
import edu.cqwu.electricity.theme.ui.LocalColorSourceState
import edu.cqwu.electricity.theme.ui.LocalNightModeState
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.util.ToastUtils
import edu.cqwu.electricity.webview.ui.UnifiedWebViewScreen

/**
 * 路由定义
 */
object Routes {
    /** 主页 Tab 容器（HorizontalPager：首页 + 我的） */
    const val MAIN_TABS = "main_tabs"
    const val SETTINGS = "settings"
    const val PERSONALIZATION = "personalization"
    const val QR_CODE_SETTINGS = "qr_code_settings"
    const val QR_LOGIN = "qr_login"
    const val WEBVPN_SETTINGS = "webvpn_settings"
    const val ELECTRICITY_MAIN = "electricity_main"
    const val DETAIL = "detail/{detailType}/{roomId}"
    const val PAYMENT_SELECTION = "payment_selection"
    const val RECHARGE_RECORD = "recharge_record/{roomId}"

    /** 扫码页面 */
    const val SCAN = "scan"

    /** 本地登录页面 */
    const val LOGIN = "login"
    /** 添加新账号（空白表单） */
    const val NEW_ACCOUNT_LOGIN = "new_account_login"

    /** Cookie 过期自动跳转登录（带从下往上覆盖动画） */
    const val COOKIE_EXPIRED_LOGIN = "cookie_expired_login"

    /** 通用内置浏览器路径 */
    const val UNIFIED_WEBVIEW = "unified_webview/{url}/{title}"

    /** 卡中心本地 UI 页面 */
    const val CARD_CENTER = "card_center"

    /** 校园卡充值 — 学号输入+金额选择页面 */
    const val CARD_RECHARGE = "card_recharge"

    /** 校园卡充值 — 支付执行页面 */
    const val CARD_PAYMENT = "card_payment"

    /** 账户信息本地 UI 页面 */
    const val ACCOUNT_INFO = "account_info"

    /** 卡挂失本地 UI 页面 */
    const val CARD_LOST = "card_lost"

    /** 账单本地 UI 页面 */
    const val BILL = "bill"

    /** 通知公告本地 UI 页面 */
    const val NOTICE = "notice"

    /** 通知公告详情本地 UI 页面 */
    const val NOTICE_DETAIL = "notice_detail/{wid}"

    /** 构建通知详情路由 */
    fun noticeDetailRoute(wid: String): String {
        return "notice_detail/$wid"
    }

    /** 二维码显示页面路径 */
    const val QR_CODE = "qr_code/{qrCodeType}"

    /** 构建二维码页面路径 */
    fun qrCodeRoute(type: QrCodeType): String {
        return "qr_code/${type.name}"
    }

    /** H5 充值统一认证地址 */
    const val H5_RECHARGE_URL = "https://authserver.cqwu.edu.cn/authserver/login?service=https%3A%2F%2Felectricitypay.cqwu.edu.cn%2Fwechat%2Fwx%2Fauth%2Flogin"

    /** H5 WebView 路由（URL 固定，无需参数）*/
    const val RECHARGE_H5_WEBVIEW = "recharge_h5_webview"

    /** 构建充值记录页路径 */
    fun rechargeRecordRoute(roomId: String): String {
        return "recharge_record/$roomId"
    }

    /** 构建详情页路径 */
    fun detailRoute(detailType: DetailType, roomId: String): String {
        return "detail/${detailType.name.lowercase()}/$roomId"
    }

    /** 关于页 */
    const val ABOUT = "about"

    /** 配置页 */
    const val CONFIG = "config"

    /** 浏览器标识设置页 */
    const val USER_AGENT_SETTINGS = "user_agent_settings"

    /** 编辑/添加浏览器标识页 */
    const val USER_AGENT_EDIT = "user_agent_edit/{entryId}"

    /** 构建编辑浏览器标识页路由 */
    fun userAgentEditRoute(entryId: String): String = "user_agent_edit/$entryId"

    /** 意见反馈页 */
    const val FEEDBACK = "feedback"

    /** 构建通用内置浏览器路由（url、title 需 URL 编码）*/
    fun unifiedWebViewRoute(url: String, title: String = ""): String {
        return "unified_webview/${java.net.URLEncoder.encode(url, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}"
    }

    /** 缴费服务大厅 */
    const val FEE_SERVICE_HALL = "fee_service_hall"

    /** 缴费服务大厅 — 直接跳转到订单 tab */
    const val FEE_SERVICE_HALL_ORDERS = "fee_service_hall_orders"

    /** 我的信息（原生页面） */
    const val MY_INFO = "my_info"

    /** 添加快捷方式 */
    const val ADD_SHORTCUT = "add_shortcut"

    /** 清除存储空间 */
    const val STORAGE_CLEAR = "storage_clear"

    /** 有话要说 — 咨询区列表 */
    const val SPEAK_UP = "speak_up"

    /** 有话要说 — 留言列表 */
    const val SPEAK_UP_MESSAGES = "speak_up_messages/{areaCode}/{areaName}"

    /** 有话要说 — 留言详情 */
    const val SPEAK_UP_DETAIL = "speak_up_detail/{wid}"

    /** 构建留言列表路由 */
    fun speakUpMessagesRoute(areaCode: String, areaName: String): String {
        return "speak_up_messages/$areaCode/${java.net.URLEncoder.encode(areaName, "UTF-8")}"
    }

    /** 构建留言详情路由 */
    fun speakUpDetailRoute(wid: String): String {
        return "speak_up_detail/$wid"
    }
}

/** 从动画设置生成过渡 EnterTransition */
private fun enterAnim(settings: AnimationSettings): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? {
    if (settings.reduceMotion == ReduceMotion.ON) return { EnterTransition.None }
    val d = 300
    return {
        when (settings.pageTransition) {
            PageTransition.NONE -> EnterTransition.None
            PageTransition.SLIDE -> slideInHorizontally(tween(d)) { it }
            PageTransition.SLIDE_VERTICAL -> slideInVertically(tween(d)) { it }
            PageTransition.FADE -> fadeIn(tween(d))
            PageTransition.FADE_SCALE -> scaleIn(tween(d), 0.9f) + fadeIn(tween(d))
            PageTransition.CUPERTINO -> slideInHorizontally(tween(350)) { it } + fadeIn(tween(350))
        }
    }
}

private fun exitAnim(settings: AnimationSettings): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? {
    if (settings.reduceMotion == ReduceMotion.ON) return { ExitTransition.None }
    val d = 300
    return {
        when (settings.pageTransition) {
            PageTransition.NONE -> ExitTransition.None
            PageTransition.SLIDE -> slideOutHorizontally(tween(d)) { -it / 5 } + fadeOut(tween(d))
            PageTransition.SLIDE_VERTICAL -> slideOutVertically(tween(d)) { -it / 5 } + fadeOut(tween(d))
            PageTransition.FADE -> fadeOut(tween(d))
            PageTransition.FADE_SCALE -> scaleOut(tween(d), 0.9f) + fadeOut(tween(d))
            PageTransition.CUPERTINO -> slideOutHorizontally(tween(350)) { -it / 4 }
        }
    }
}

private fun popEnterAnim(settings: AnimationSettings): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? {
    if (settings.reduceMotion == ReduceMotion.ON) return { EnterTransition.None }
    val d = 300
    return {
        when (settings.pageTransition) {
            PageTransition.NONE -> EnterTransition.None
            PageTransition.SLIDE -> slideInHorizontally(tween(d)) { -it / 5 } + fadeIn(tween(d))
            PageTransition.SLIDE_VERTICAL -> slideInVertically(tween(d)) { -it / 5 } + fadeIn(tween(d))
            PageTransition.FADE -> fadeIn(tween(d))
            PageTransition.FADE_SCALE -> scaleIn(tween(d), 0.9f) + fadeIn(tween(d))
            PageTransition.CUPERTINO -> slideInHorizontally(tween(350)) { -it / 4 }
        }
    }
}

private fun popExitAnim(settings: AnimationSettings): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? {
    if (settings.reduceMotion == ReduceMotion.ON) return { ExitTransition.None }
    val d = 300
    return {
        when (settings.pageTransition) {
            PageTransition.NONE -> ExitTransition.None
            PageTransition.SLIDE -> slideOutHorizontally(tween(d)) { it }
            PageTransition.SLIDE_VERTICAL -> slideOutVertically(tween(d)) { it }
            PageTransition.FADE -> fadeOut(tween(d))
            PageTransition.FADE_SCALE -> scaleOut(tween(d), 0.9f) + fadeOut(tween(d))
            PageTransition.CUPERTINO -> slideOutHorizontally(tween(350)) { it } + fadeOut(tween(350))
        }
    }
}

/**
 * 动画路由——自动附加页面过渡动画。
 * 调用方在 [AppNavGraph] 中通过 [LocalAnimationSettings] 获取设置后传入。
 */
private fun NavGraphBuilder.animatedComposable(
    settings: AnimationSettings,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = enterAnim(settings),
        exitTransition = exitAnim(settings),
        popEnterTransition = popEnterAnim(settings),
        popExitTransition = popExitAnim(settings),
        content = content,
    )
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    shortcutAppInfo: edu.cqwu.electricity.shortcut.util.ShortcutHelper.ShortcutAppInfo? = null,
    shortcutLaunchId: Int = 0,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val viewModel: ElectricityViewModel = viewModel()
    val rechargeViewModel: RechargeViewModel = viewModel()
    val cardRechargeViewModel: CardRechargeViewModel = viewModel()
    val myRoomViewModel: MyRoomViewModel = viewModel()
    val noticeViewModel: NoticeViewModel = viewModel()
    var skipNextCasRedirect by rememberSaveable { mutableStateOf(false) }
    val nightModeState = LocalNightModeState.current
    val colorSourceState = LocalColorSourceState.current
    val animationSettings = LocalAnimationSettings.current
    val topBarState = LocalTopBarState.current

    val snackbar = LocalSnackbarController.current

    // 启动时后台静默验证 Cookie 有效性
    LaunchedEffect(Unit) {
        val store = UserCookieStore() // 空 store，validate 内部会从系统 CookieManager 兜底
        when (val result = SessionManager.validateCookie(store)) {
            is SessionValidationResult.Valid -> {
                android.util.Log.d("NavGraph", "启动 Cookie 验证：有效")
            }
            is SessionValidationResult.Invalid -> {
                android.util.Log.d("NavGraph", "启动 Cookie 验证：失效，跳转登录页")
                navController.navigate(Routes.COOKIE_EXPIRED_LOGIN)
            }
            is SessionValidationResult.NetworkError -> {
                android.util.Log.w("NavGraph", "启动 Cookie 验证：网络错误 - ${result.message}")
                snackbar.show("网络异常，请检查网络连接", ToastUtils.Type.ERROR)
            }
        }
    }

    // 处理桌面快捷方式启动的导航
    LaunchedEffect(shortcutLaunchId) {
        if (shortcutAppInfo != null) {
            val appId = shortcutAppInfo.appId
            val openUrl = shortcutAppInfo.openUrl
            when (appId) {
                edu.cqwu.electricity.home.data.HomeAppIds.PAY_QR ->
                    navController.navigate(Routes.qrCodeRoute(QrCodeType.PAY))
                edu.cqwu.electricity.home.data.HomeAppIds.BUS_QR ->
                    navController.navigate(Routes.qrCodeRoute(QrCodeType.BUS))
                edu.cqwu.electricity.home.data.HomeAppIds.DORM_ELECTRICITY ->
                    navController.navigate(Routes.ELECTRICITY_MAIN)
                edu.cqwu.electricity.home.data.HomeAppIds.CARD_CENTER ->
                    navController.navigate(Routes.CARD_CENTER)
                edu.cqwu.electricity.home.data.HomeAppIds.NOTICE ->
                    navController.navigate(Routes.NOTICE)
                edu.cqwu.electricity.home.data.HomeAppIds.FEE_SERVICE_HALL ->
                    navController.navigate(Routes.FEE_SERVICE_HALL)
                edu.cqwu.electricity.home.data.HomeAppIds.MY_INFO ->
                    navController.navigate(Routes.MY_INFO)
                edu.cqwu.electricity.home.data.HomeAppIds.SPEAK_UP ->
                    navController.navigate(Routes.SPEAK_UP)
                else -> {
                    // 网页类功能 → 在内置浏览器中打开
                    if (openUrl.isNotBlank()) {
                        navController.navigate(Routes.unifiedWebViewRoute(openUrl, shortcutAppInfo.appName))
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN_TABS,
        modifier = modifier,
    ) {
        // 主页 Tab 容器（HorizontalPager：首页 + 我的）
        animatedComposable(settings = animationSettings, route = Routes.MAIN_TABS) {
            MainTabScreen(
                animationSettings = animationSettings,
            )
        }

        // 缴费服务大厅
        animatedComposable(settings = animationSettings, route = Routes.FEE_SERVICE_HALL) {
            FeeServiceHallScreen(
                onBack = { navController.popBackStack() },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
            )
        }

        // 缴费服务大厅 — 订单 tab
        animatedComposable(settings = animationSettings, route = Routes.FEE_SERVICE_HALL_ORDERS) {
            FeeServiceHallScreen(
                onBack = { navController.popBackStack() },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
                initialTab = 1,
            )
        }

        // 我的信息（原生页面） */
        animatedComposable(settings = animationSettings, route = Routes.MY_INFO) {
            MyInfoScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
            )
        }

        // 卡中心
        animatedComposable(settings = animationSettings, route = Routes.CARD_CENTER) {
            CardCenterScreen(
                onBack = { navController.popBackStack() },
                onNavigateToQrCode = { type -> navController.navigate(Routes.qrCodeRoute(type)) },
                onNavigateToCardRecharge = { navController.navigate(Routes.CARD_RECHARGE) { launchSingleTop = true } },
            )
        }

        // 校园卡充值 — 学号输入+金额选择
        animatedComposable(settings = animationSettings, route = Routes.CARD_RECHARGE) {
            CardRechargeScreen(
                viewModel = cardRechargeViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPayment = { navController.navigate(Routes.CARD_PAYMENT) { launchSingleTop = true } },
            )
        }

        // 校园卡充值 — 支付执行（使用 AppNavGraph 级别的共享 ViewModel）
        animatedComposable(settings = animationSettings, route = Routes.CARD_PAYMENT) {
            CardPaymentScreen(
                viewModel = cardRechargeViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // 账户信息
        animatedComposable(settings = animationSettings, route = Routes.ACCOUNT_INFO) {
            AccountInfoScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.LOGIN) },
            )
        }

        // 卡挂失
        animatedComposable(settings = animationSettings, route = Routes.CARD_LOST) {
            CardLostScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.LOGIN) },
            )
        }

        // 账单
        animatedComposable(settings = animationSettings, route = Routes.BILL) {
            val billViewModel: BillViewModel = viewModel()
            BillScreen(
                viewModel = billViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
                onReLogin = { navController.navigate(Routes.LOGIN) },
            )
        }

        // 通知公告
        animatedComposable(settings = animationSettings, route = Routes.NOTICE) {
            NoticeScreen(
                viewModel = noticeViewModel,
                onBack = { noticeViewModel.listRefreshEnabled = true; navController.popBackStack() },
                onNavigateToNoticeDetail = { wid -> navController.navigate(Routes.noticeDetailRoute(wid)) },
            )
        }

        // 通知公告详情
        animatedComposable(
            settings = animationSettings,
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
                viewModel = noticeViewModel
            )
        }

        // 通用内置浏览器
        animatedComposable(
            settings = animationSettings,
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
                skipNextCasRedirect = skipNextCasRedirect,
                onSkipConsumed = { skipNextCasRedirect = false },
            )
        }

        animatedComposable(settings = animationSettings, route = Routes.ELECTRICITY_MAIN) {
            ElectricityMainScreen(
                viewModel = viewModel,
                rechargeViewModel = rechargeViewModel,
                myRoomViewModel = myRoomViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        animatedComposable(
            settings = animationSettings,
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("detailType") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val detailTypeStr = backStackEntry.arguments?.getString("detailType") ?: ""
            val detailType = remember(detailTypeStr) {
                DetailType.entries.firstOrNull { it.name.lowercase() == detailTypeStr }
                    ?: DetailType.SIX_MONTH_USAGE
            }
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val detailViewModel: DetailViewModel = viewModel(
                key = "detail_${detailType.name}_$roomId",
                factory = DetailViewModel.Factory(roomId)
            )
            DetailScreen(viewModel = detailViewModel, detailType = detailType, onBack = { navController.popBackStack() })
        }


        animatedComposable(settings = animationSettings, route = Routes.PAYMENT_SELECTION) {
            PaymentSelectionScreen(
                viewModel = rechargeViewModel,
                onBack = { navController.popBackStack() },
                onPaymentComplete = { navController.popBackStack(Routes.ELECTRICITY_MAIN, false) },
            )
        }

        animatedComposable(
            settings = animationSettings,
            route = Routes.RECHARGE_RECORD,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            RechargeRecordScreen(
                viewModel = rechargeViewModel,
                roomId = roomId,
                onBack = { navController.popBackStack() }
            )
        }

        // H5 WebView 路由
        animatedComposable(settings = animationSettings, route = Routes.RECHARGE_H5_WEBVIEW) {
            UnifiedWebViewScreen(
                url = Routes.H5_RECHARGE_URL,
                onClose = { navController.popBackStack(Routes.ELECTRICITY_MAIN, false) },
            )
        }

        // 二维码显示页面
        animatedComposable(
            settings = animationSettings,
            route = Routes.QR_CODE,
            arguments = listOf(navArgument("qrCodeType") { type = NavType.StringType }),
        ) { backStackEntry ->
            val typeStr = backStackEntry.arguments?.getString("qrCodeType") ?: "PAY"
            val qrCodeType = remember(typeStr) { try { QrCodeType.valueOf(typeStr) } catch (_: Exception) { QrCodeType.PAY } }
            val title = when (qrCodeType) { QrCodeType.PAY -> stringResource(R.string.card_center_payment_code); QrCodeType.BUS -> stringResource(R.string.card_center_transit_code) }
            QrCodeDisplayScreen(
                qrCodeType = qrCodeType, title = title,
                onBack = { navController.popBackStack() },
            )
        }

        // 本地登录页面（通用入口，自动填充最近账号）
        animatedComposable(settings = animationSettings, route = Routes.LOGIN) {
            LoginScreen(
                onBack = { skipNextCasRedirect = true; navController.popBackStack() },
            )
        }

        // 添加新账号（空白表单）
        animatedComposable(settings = animationSettings, route = Routes.NEW_ACCOUNT_LOGIN) {
            LoginScreen(
                clearForm = true,
                onBack = { skipNextCasRedirect = true; navController.popBackStack() },
            )
        }

        // Cookie 过期自动跳转登录（从下往上覆盖 / 从上往下退出，由快变慢）
        composable(
            route = Routes.COOKIE_EXPIRED_LOGIN,
            enterTransition = {
                slideInVertically(
                    animationSpec = tween(800, easing = LinearEasing),
                    initialOffsetY = { it }
                ) + fadeIn(tween(200))
            },
            exitTransition = exitAnim(animationSettings),
            popExitTransition = exitAnim(animationSettings),
        ) {
            LoginScreen(
                onBack = { skipNextCasRedirect = true; navController.popBackStack() },
            )
        }

        // 扫码登录页面
        animatedComposable(settings = animationSettings, route = Routes.QR_LOGIN) {
            QrLoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = { navController.popBackStack() },
                onNavigateToQrCodeSettings = { navController.navigate(Routes.QR_CODE_SETTINGS) },
            )
        }

        // 设置页面
        animatedComposable(settings = animationSettings, route = Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 关于页面
        animatedComposable(settings = animationSettings, route = Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 配置页
        animatedComposable(settings = animationSettings, route = Routes.CONFIG) {
            ConfigScreen(
                onBack = { navController.popBackStack() },
                onNavigateToUserAgent = { navController.navigate(Routes.USER_AGENT_SETTINGS) },
                onNavigateToStorageClear = { navController.navigate(Routes.STORAGE_CLEAR) },
                onNavigateToWebVpn = { navController.navigate(Routes.WEBVPN_SETTINGS) },
            )
        }

        // WebVPN 设置页
        animatedComposable(settings = animationSettings, route = Routes.WEBVPN_SETTINGS) {
            WebVpnSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 清除存储空间页
        animatedComposable(settings = animationSettings, route = Routes.STORAGE_CLEAR) {
            StorageClearScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 浏览器标识设置页
        animatedComposable(settings = animationSettings, route = Routes.USER_AGENT_SETTINGS) {
            UserAgentSettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEdit = { entryId -> navController.navigate(Routes.userAgentEditRoute(entryId)) },
            )
        }

        // 编辑/添加浏览器标识页
        animatedComposable(
            settings = animationSettings,
            route = Routes.USER_AGENT_EDIT,
            arguments = listOf(navArgument("entryId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId") ?: "new"
            UserAgentEditScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
            )
        }

        // 个性化设置页面
        animatedComposable(settings = animationSettings, route = Routes.PERSONALIZATION) {
            PersonalizationScreen(
                onBack = { navController.popBackStack() },
                currentNightMode = nightModeState.nightMode,
                onNightModeChange = { mode -> nightModeState.onNightModeChange(mode) },
                currentColorSource = colorSourceState.colorSource,
                onColorSourceChange = { source -> colorSourceState.onColorSourceChange(source) },
                currentPageTransition = animationSettings.pageTransition,
                onPageTransitionChange = { mode -> animationSettings.onPageTransitionChange(mode) },
                currentReduceMotion = animationSettings.reduceMotion,
                onReduceMotionChange = { mode -> animationSettings.onReduceMotionChange(mode) },
                currentTopBarStyle = topBarState.style,
                onTopBarStyleChange = { style -> topBarState.onStyleChange(style) },
                onNavigateToQrCodeSettings = { navController.navigate(Routes.QR_CODE_SETTINGS) },
                isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            )
        }

        // 二维码设置页面
        animatedComposable(settings = animationSettings, route = Routes.QR_CODE_SETTINGS) {
            QrCodeSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 意见反馈页面
        // 扫码页面
        animatedComposable(settings = animationSettings, route = Routes.SCAN) {
            ScanScreen(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    navController.popBackStack()
                    navController.navigate(Routes.unifiedWebViewRoute(url, resources.getString(R.string.scan_title)))
                },
            )
        }

        animatedComposable(settings = animationSettings, route = Routes.FEEDBACK) {
            FeedbackScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 添加快捷方式页面
        animatedComposable(settings = animationSettings, route = Routes.ADD_SHORTCUT) {
            AddShortcutScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 有话要说 — 咨询区列表
        animatedComposable(settings = animationSettings, route = Routes.SPEAK_UP) {
            SpeakUpScreen(
                onBack = { navController.popBackStack() },
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
            settings = animationSettings,
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
                onMessageClick = { wid ->
                    navController.navigate(Routes.speakUpDetailRoute(wid))
                },
            )
        }

        // 有话要说 — 留言详情
        animatedComposable(
            settings = animationSettings,
            route = Routes.SPEAK_UP_DETAIL,
            arguments = listOf(
                navArgument("wid") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val wid = backStackEntry.arguments?.getString("wid") ?: ""
            MessageDetailScreen(
                wid = wid,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
