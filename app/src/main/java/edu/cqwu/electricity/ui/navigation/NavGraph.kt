package edu.cqwu.electricity.ui.navigation

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.os.Build
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import edu.cqwu.electricity.data.local.PageTransition
import edu.cqwu.electricity.data.local.ReduceMotion
import edu.cqwu.electricity.data.model.DetailType
import edu.cqwu.electricity.data.network.QrCodeType
import edu.cqwu.electricity.ui.cardcenter.AccountInfoScreen
import edu.cqwu.electricity.ui.cardcenter.BillScreen
import edu.cqwu.electricity.ui.cardcenter.BillViewModel
import edu.cqwu.electricity.ui.cardcenter.CardCenterScreen
import edu.cqwu.electricity.ui.cardcenter.CardLostScreen
import edu.cqwu.electricity.ui.electricity.BuildingSelectionScreen
import edu.cqwu.electricity.ui.electricity.DashboardScreen
import edu.cqwu.electricity.ui.electricity.DetailScreen
import edu.cqwu.electricity.ui.electricity.DetailViewModel
import edu.cqwu.electricity.ui.electricity.ElectricityMainScreen
import edu.cqwu.electricity.ui.electricity.ElectricityViewModel
import edu.cqwu.electricity.ui.feedback.FeedbackScreen
import edu.cqwu.electricity.ui.feeservicehall.FeeServiceHallScreen
import edu.cqwu.electricity.ui.profile.MyInfoScreen
import edu.cqwu.electricity.ui.login.LoginScreen
import edu.cqwu.electricity.ui.login.QrLoginScreen
import edu.cqwu.electricity.ui.myroom.MyRoomViewModel
import edu.cqwu.electricity.ui.notice.NoticeDetailScreen
import edu.cqwu.electricity.ui.notice.NoticeScreen
import edu.cqwu.electricity.ui.notice.NoticeViewModel
import edu.cqwu.electricity.ui.qrcode.QrCodeDisplayScreen
import edu.cqwu.electricity.ui.recharge.PaymentSelectionScreen
import edu.cqwu.electricity.ui.recharge.RechargeRecordScreen
import edu.cqwu.electricity.ui.recharge.RechargeScreen
import edu.cqwu.electricity.ui.recharge.RechargeViewModel
import edu.cqwu.electricity.ui.scan.ScanScreen
import edu.cqwu.electricity.ui.settings.AboutScreen
import edu.cqwu.electricity.ui.settings.ConfigScreen
import edu.cqwu.electricity.ui.settings.PersonalizationScreen
import edu.cqwu.electricity.ui.settings.QrCodeSettingsScreen
import edu.cqwu.electricity.ui.settings.SettingsScreen
import edu.cqwu.electricity.ui.settings.UserAgentEditScreen
import edu.cqwu.electricity.ui.settings.UserAgentSettingsScreen
import edu.cqwu.electricity.ui.theme.AnimationSettings
import edu.cqwu.electricity.ui.theme.LocalAnimationSettings
import edu.cqwu.electricity.ui.theme.LocalColorSourceState
import edu.cqwu.electricity.ui.theme.LocalNightModeState
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.webview.UnifiedWebViewScreen

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
    const val BUILDING_SELECTION = "building_selection"
    const val ELECTRICITY_MAIN = "electricity_main"
    const val DASHBOARD = "dashboard"
    const val DETAIL = "detail/{detailType}/{roomId}"
    const val RECHARGE = "recharge"
    const val PAYMENT_SELECTION = "payment_selection"
    const val RECHARGE_RECORD = "recharge_record/{roomId}"

    /** 扫码页面 */
    const val SCAN = "scan"

    /** 本地登录页面 */
    const val LOGIN = "login"

    /** 通用内置浏览器路径 */
    const val UNIFIED_WEBVIEW = "unified_webview/{url}/{title}"

    /** 卡中心本地 UI 页面 */
    const val CARD_CENTER = "card_center"

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

    /** 我的信息（原生页面） */
    const val MY_INFO = "my_info"
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
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: ElectricityViewModel = viewModel()
    val rechargeViewModel: RechargeViewModel = viewModel()
    val myRoomViewModel: MyRoomViewModel = viewModel()
    val noticeViewModel: NoticeViewModel = viewModel()
    var skipNextCasRedirect by rememberSaveable { mutableStateOf(false) }
    val nightModeState = LocalNightModeState.current
    val colorSourceState = LocalColorSourceState.current
    val animationSettings = LocalAnimationSettings.current
    val topBarState = LocalTopBarState.current

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN_TABS,
        modifier = modifier,
    ) {
        // 主页 Tab 容器（HorizontalPager：首页 + 我的）
        animatedComposable(settings = animationSettings, route = Routes.MAIN_TABS) {
            MainTabScreen(
                animationSettings = animationSettings,
                onNavigateToBuildingSelection = { navController.navigate(Routes.ELECTRICITY_MAIN) },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToScan = { navController.navigate(Routes.SCAN) },
                onNavigateToQrCode = { type -> navController.navigate(Routes.qrCodeRoute(type)) },
                onNavigateToCardCenter = { navController.navigate(Routes.CARD_CENTER) },
                onNavigateToNotice = { navController.navigate(Routes.NOTICE) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToFeedback = { navController.navigate(Routes.FEEDBACK) },
                onNavigateToFeeServiceHall = { navController.navigate(Routes.FEE_SERVICE_HALL) },
                onNavigateToMyInfo = { navController.navigate(Routes.MY_INFO) },
            )
        }

        // 缴费服务大厅
        animatedComposable(settings = animationSettings, route = Routes.FEE_SERVICE_HALL) {
            FeeServiceHallScreen(
                onBack = { navController.popBackStack() },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
            )
        }

        // 我的信息（原生页面）
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
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
                onNavigateToAccountInfo = { navController.navigate(Routes.ACCOUNT_INFO) },
                onNavigateToCardLost = { navController.navigate(Routes.CARD_LOST) },
                onNavigateToBill = { navController.navigate(Routes.BILL) },
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
            val url = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("url") ?: "", "UTF-8")
            val title = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", "UTF-8")
            UnifiedWebViewScreen(
                url = url,
                initialTitle = title.ifBlank { "" },
                onClose = { navController.popBackStack() },
                onNavigateToLogin = { skipNextCasRedirect = false; navController.navigate(Routes.LOGIN) },
                onNavigateToWebView = { newUrl, newTitle ->
                    navController.navigate(Routes.unifiedWebViewRoute(newUrl, newTitle))
                },
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
                onNavigateToDetail = { detailType, roomId -> navController.navigate(Routes.detailRoute(detailType, roomId)) },
                onNavigateToPayment = { navController.navigate(Routes.PAYMENT_SELECTION) },
                onNavigateToH5Recharge = { navController.navigate(Routes.RECHARGE_H5_WEBVIEW) },
                onNavigateToRechargeRecord = { roomId -> navController.navigate(Routes.rechargeRecordRoute(roomId)) },
            )
        }

        // 保留旧的 BUILDING_SELECTION + DASHBOARD 路由支持（Tab 容器内部使用的独立页面）：
        // 当从其他页面直接导航到这些路由时仍然可用
        animatedComposable(settings = animationSettings, route = Routes.BUILDING_SELECTION) {
            BuildingSelectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToAccountSelection = { navController.navigate(Routes.RECHARGE) },
            )
        }

        animatedComposable(settings = animationSettings, route = Routes.DASHBOARD) {
            val s by viewModel.uiState.collectAsState()
            DashboardScreen(
                room = s.selectedRoom,
                balance = s.balance,
                myRoomList = emptyList(),
                isRefreshing = s.isBalanceRefreshing,
                isLoading = s.isLoading,
                error = s.error,
                onRefresh = { viewModel.refreshBalance() },
                onBackToSelection = { navController.popBackStack(Routes.BUILDING_SELECTION, false) },
                onNavigateToDetail = { detailType ->
                    val roomId = s.selectedRoom?.id ?: ""
                    navController.navigate(Routes.detailRoute(detailType, roomId))
                },
                onNavigateToAccountSelection = { navController.navigate(Routes.RECHARGE) },
                onNavigateToH5Recharge = { navController.navigate(Routes.RECHARGE_H5_WEBVIEW) },
                onNavigateToRechargeRecord = {
                    val roomId = s.selectedRoom?.id ?: ""
                    navController.navigate(Routes.rechargeRecordRoute(roomId))
                },
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

        animatedComposable(settings = animationSettings, route = Routes.RECHARGE) {
            RechargeScreen(
                viewModel = rechargeViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPayment = { navController.navigate(Routes.PAYMENT_SELECTION) },
                onNavigateToH5Recharge = { navController.navigate(Routes.RECHARGE_H5_WEBVIEW) },
            )
        }

        animatedComposable(settings = animationSettings, route = Routes.PAYMENT_SELECTION) {
            PaymentSelectionScreen(
                viewModel = rechargeViewModel,
                onBack = { navController.popBackStack() },
                onPaymentComplete = { navController.popBackStack(Routes.DASHBOARD, false) },
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
                onClose = { navController.popBackStack(Routes.DASHBOARD, false) },
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
                onNavigateToLogin = { navController.navigate(Routes.LOGIN) },
                onNavigateToQrCodeSettings = { navController.navigate(Routes.QR_CODE_SETTINGS) },
            )
        }

        // 本地登录页面
        animatedComposable(settings = animationSettings, route = Routes.LOGIN) {
            LoginScreen(
                onBack = { skipNextCasRedirect = true; navController.popBackStack() },
                onNavigateToQrLogin = { navController.navigate(Routes.QR_LOGIN) },
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
                onNavigateToPersonalization = { navController.navigate(Routes.PERSONALIZATION) },
                onNavigateToConfig = { navController.navigate(Routes.CONFIG) },
                onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
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
                    navController.navigate(Routes.unifiedWebViewRoute(url, context.getString(R.string.scan_title)))
                },
            )
        }

        animatedComposable(settings = animationSettings, route = Routes.FEEDBACK) {
            FeedbackScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
