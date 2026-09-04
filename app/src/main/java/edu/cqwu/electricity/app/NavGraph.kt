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
import androidx.compose.runtime.CompositionLocalProvider
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
import edu.cqwu.electricity.accountmanagerv2.AccountManagerScreen
import edu.cqwu.electricity.accountmanagerv2.DeviceSessionScreen
import edu.cqwu.electricity.accountmanagerv2.LoginLogScreen
import edu.cqwu.electricity.accountmanagerv2.PasswordChangeScreen
import edu.cqwu.electricity.accountmanagerv2.UserNameEditScreen
import edu.cqwu.electricity.cardcenter.ui.AccountInfoScreen
import edu.cqwu.electricity.cardcenter.ui.BankCardBindScreen
import edu.cqwu.electricity.cardcenter.ui.BankCardBindViewModel
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
import edu.cqwu.electricity.electricity.ui.SubsidyRecordScreen
import edu.cqwu.electricity.electricity.ui.SubsidyRecordViewModel
import edu.cqwu.electricity.electricity.ui.UsageRecordScreenV2
import edu.cqwu.electricity.electricity.ui.UsageRecordViewModelV2
import edu.cqwu.electricity.feedback.ui.FeedbackScreen
import edu.cqwu.electricity.feeservicehall.ui.FeeServiceHallScreen
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
import edu.cqwu.electricity.person.ui.PersonSearchScreen
import edu.cqwu.electricity.theme.ui.AppSettingsState
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState
import edu.cqwu.electricity.theme.ui.LocalWebViewReloadAfterLogin
import edu.cqwu.electricity.theme.ui.LocalWebViewReloadConsumed
import edu.cqwu.electricity.webview.ui.UnifiedWebViewScreen

/**
 * 路由定义
 */
object Routes {
    /** 主页 Tab 容器（HorizontalPager：首页 + 我的） */
    const val MAIN_TABS = "main_tabs"
    const val SETTINGS = "settings"
    /** 账号管理页（独立页面，代码位于 accountmanagerv2 包） */
    const val ACCOUNT_MANAGER = "account_manager"
    /** 修改用户名页（登录别名 + 昵称） */
    const val USER_NAME_EDIT = "user_name_edit"
    /** 修改密码页 */
    const val PASSWORD_CHANGE = "password_change"
    /** 登录设备管理页（在线会话查看/踢出） */
    const val DEVICE_SESSION = "device_session"
    /** 日志记录页（登录/维护日志查看与筛选） */
    const val LOGIN_LOG = "login_log"
    const val PERSONALIZATION = "personalization"
    const val QR_CODE_SETTINGS = "qr_code_settings"
    const val QR_LOGIN = "qr_login"
    const val WEBVPN_SETTINGS = "webvpn_settings"
    const val ELECTRICITY_MAIN = "electricity_main"
    const val DETAIL = "detail/{detailType}/{roomId}"
    const val PAYMENT_SELECTION = "payment_selection"
    const val RECHARGE_RECORD = "recharge_record/{roomId}"
    const val USAGE_RECORD = "usage_record/{roomId}"
    const val SUBSIDY_RECORD = "subsidy_record/{roomId}"

    /** 扫码页面 */
    const val SCAN = "scan"

    /** 本地登录页面（支持可选 accountId 参数，预填指定账号条目） */
    const val LOGIN = "login?accountId={accountId}"

    /** 构造登录页路由；accountId 为空时登录页自动填充当前激活条目 */
    fun loginRoute(accountId: String = ""): String = "login?accountId=$accountId"
    /** 从内置浏览器进入的本地登录页面（返回时同时关闭 WebView） */
    const val WEBVIEW_LOGIN = "webview_login"
    /** 添加新账号（空白表单） */
    const val NEW_ACCOUNT_LOGIN = "new_account_login"

    /** Cookie 过期自动跳转登录（带从下往上覆盖动画） */
    const val COOKIE_EXPIRED_LOGIN = "cookie_expired_login"

    /** 通用内置浏览器路径 */
    const val UNIFIED_WEBVIEW = "unified_webview/{url}/{title}"

    /** 卡中心本地 UI 页面 */
    const val CARD_CENTER = "card_center"

    /** 学生绑定银行卡本地 UI 页面 */
    const val BANK_CARD_BIND = "bank_card_bind"

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

    /** 构建用量报表页路径 */
    fun usageRecordRoute(roomId: String): String {
        return "usage_record/$roomId"
    }

    /** 构建补助记录页路径 */
    fun subsidyRecordRoute(roomId: String): String {
        return "subsidy_record/$roomId"
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

    /** 查找人员（原生页面） */
    const val PERSON_SEARCH = "person_search"
}

/** 从动画设置生成过渡 EnterTransition */
private fun enterAnim(settings: AppSettingsState): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? {
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

private fun exitAnim(settings: AppSettingsState): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? {
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

private fun popEnterAnim(settings: AppSettingsState): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? {
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

private fun popExitAnim(settings: AppSettingsState): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? {
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
 * 调用方在 [AppNavGraph] 中通过 [LocalAppSettingsState] 获取设置后传入。
 */
private fun NavGraphBuilder.animatedComposable(
    settings: AppSettingsState,
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
    val resources = LocalResources.current
    val viewModel: ElectricityViewModel = viewModel()
    val rechargeViewModel: RechargeViewModel = viewModel()
    val cardRechargeViewModel: CardRechargeViewModel = viewModel()
    val myRoomViewModel: MyRoomViewModel = viewModel()
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
        // 主页 Tab 容器（HorizontalPager：首页 + 我的）
        animatedComposable(settings = appSettings, route = Routes.MAIN_TABS) {
            MainTabScreen()
        }

        // 缴费服务大厅
        animatedComposable(settings = appSettings, route = Routes.FEE_SERVICE_HALL) {
            FeeServiceHallScreen(
                onBack = { navController.popBackStack() },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 缴费服务大厅 — 订单 tab
        animatedComposable(settings = appSettings, route = Routes.FEE_SERVICE_HALL_ORDERS) {
            FeeServiceHallScreen(
                onBack = { navController.popBackStack() },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
                initialTab = 1,
            )
        }

        // 我的信息（原生页面） */
        animatedComposable(settings = appSettings, route = Routes.MY_INFO) {
            MyInfoScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
            )
        }

        // 卡中心
        animatedComposable(settings = appSettings, route = Routes.CARD_CENTER) {
            CardCenterScreen(
                onBack = { navController.popBackStack() },
                onNavigateToQrCode = { type -> navController.navigate(Routes.qrCodeRoute(type)) },
                onNavigateToCardRecharge = { navController.navigate(Routes.CARD_RECHARGE) { launchSingleTop = true } },
            )
        }

        // 学生绑定银行卡
        animatedComposable(settings = appSettings, route = Routes.BANK_CARD_BIND) {
            val bankCardBindViewModel: BankCardBindViewModel = viewModel()
            BankCardBindScreen(
                viewModel = bankCardBindViewModel,
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 校园卡充值 — 学号输入+金额选择
        animatedComposable(settings = appSettings, route = Routes.CARD_RECHARGE) {
            CardRechargeScreen(
                viewModel = cardRechargeViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToPayment = { navController.navigate(Routes.CARD_PAYMENT) { launchSingleTop = true } },
            )
        }

        // 校园卡充值 — 支付执行（使用 AppNavGraph 级别的共享 ViewModel）
        animatedComposable(settings = appSettings, route = Routes.CARD_PAYMENT) {
            CardPaymentScreen(
                viewModel = cardRechargeViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // 账户信息
        animatedComposable(settings = appSettings, route = Routes.ACCOUNT_INFO) {
            AccountInfoScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 卡挂失
        animatedComposable(settings = appSettings, route = Routes.CARD_LOST) {
            CardLostScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 账单
        animatedComposable(settings = appSettings, route = Routes.BILL) {
            val billViewModel: BillViewModel = viewModel()
            BillScreen(
                viewModel = billViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 通知公告
        animatedComposable(settings = appSettings, route = Routes.NOTICE) {
            NoticeScreen(
                viewModel = noticeViewModel,
                onBack = { noticeViewModel.listRefreshEnabled = true; navController.popBackStack() },
                onNavigateToNoticeDetail = { wid -> navController.navigate(Routes.noticeDetailRoute(wid)) },
            )
        }

        // 通知公告详情
        animatedComposable(
            settings = appSettings,
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

        animatedComposable(settings = appSettings, route = Routes.ELECTRICITY_MAIN) {
            ElectricityMainScreen(
                viewModel = viewModel,
                rechargeViewModel = rechargeViewModel,
                myRoomViewModel = myRoomViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        animatedComposable(
            settings = appSettings,
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("detailType") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val detailViewModel: DetailViewModel = viewModel(
                key = "detail_${DetailType.METER_STATUS.name}_$roomId",
                factory = DetailViewModel.Factory(roomId)
            )
            DetailScreen(viewModel = detailViewModel, detailType = DetailType.METER_STATUS, onBack = { navController.popBackStack() })
        }


        animatedComposable(settings = appSettings, route = Routes.PAYMENT_SELECTION) {
            PaymentSelectionScreen(
                viewModel = rechargeViewModel,
                onBack = { navController.popBackStack() },
                onPaymentComplete = { navController.popBackStack(Routes.ELECTRICITY_MAIN, false) },
            )
        }

        animatedComposable(
            settings = appSettings,
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

        animatedComposable(
            settings = appSettings,
            route = Routes.USAGE_RECORD,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val usageRecordViewModel: UsageRecordViewModelV2 = viewModel(
                key = "usage_record_$roomId",
                factory = UsageRecordViewModelV2.Factory(roomId)
            )
            UsageRecordScreenV2(
                viewModel = usageRecordViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        animatedComposable(
            settings = appSettings,
            route = Routes.SUBSIDY_RECORD,
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val subsidyRecordViewModel: SubsidyRecordViewModel = viewModel(
                key = "subsidy_record_$roomId",
                factory = SubsidyRecordViewModel.Factory(roomId)
            )
            SubsidyRecordScreen(
                viewModel = subsidyRecordViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // H5 WebView 路由
        animatedComposable(settings = appSettings, route = Routes.RECHARGE_H5_WEBVIEW) {
            UnifiedWebViewScreen(
                url = Routes.H5_RECHARGE_URL,
                onClose = { navController.popBackStack(Routes.ELECTRICITY_MAIN, false) },
            )
        }

        // 二维码显示页面
        animatedComposable(
            settings = appSettings,
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

        // 本地登录页面（通用入口；可选 accountId 参数预填指定账号条目，为空时自动填充当前激活条目）
        animatedComposable(
            settings = appSettings,
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
        animatedComposable(settings = appSettings, route = Routes.NEW_ACCOUNT_LOGIN) {
            LoginScreen(
                clearForm = true,
                onBack = { navController.popBackStack() },
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
            exitTransition = exitAnim(appSettings),
            popExitTransition = exitAnim(appSettings),
        ) {
            LoginScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 扫码登录页面
        animatedComposable(settings = appSettings, route = Routes.QR_LOGIN) {
            QrLoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = { navController.popBackStack() },
                onNavigateToQrCodeSettings = { navController.navigate(Routes.QR_CODE_SETTINGS) },
            )
        }

        // 设置页面
        animatedComposable(settings = appSettings, route = Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 账号管理页（独立页面，代码位于 accountmanagerv2 包）
        animatedComposable(settings = appSettings, route = Routes.ACCOUNT_MANAGER) {
            AccountManagerScreen(
                onBack = { navController.popBackStack() },
                onNavigateToLogin = { accountId -> navController.navigate(Routes.loginRoute(accountId)) },
                onNavigateToAddAccount = { navController.navigate(Routes.NEW_ACCOUNT_LOGIN) },
                onNavigateToUserNameEdit = { navController.navigate(Routes.USER_NAME_EDIT) },
                onNavigateToPasswordEdit = { navController.navigate(Routes.PASSWORD_CHANGE) },
                onNavigateToDeviceSession = { navController.navigate(Routes.DEVICE_SESSION) },
                onNavigateToLoginLog = { navController.navigate(Routes.LOGIN_LOG) },
            )
        }

        // 修改用户名页（登录别名 + 昵称，本地化 CAS mobileUserAttrEdit.do）
        animatedComposable(settings = appSettings, route = Routes.USER_NAME_EDIT) {
            UserNameEditScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 修改密码页（本地化 CAS mobilePasswordChange.do）
        animatedComposable(settings = appSettings, route = Routes.PASSWORD_CHANGE) {
            PasswordChangeScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 登录设备管理页（本地化 CAS userOnline.do）
        animatedComposable(settings = appSettings, route = Routes.DEVICE_SESSION) {
            DeviceSessionScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 日志记录页（本地化 CAS userLogs.do）
        animatedComposable(settings = appSettings, route = Routes.LOGIN_LOG) {
            LoginLogScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 关于页面
        animatedComposable(settings = appSettings, route = Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 配置页
        animatedComposable(settings = appSettings, route = Routes.CONFIG) {
            ConfigScreen(
                onBack = { navController.popBackStack() },
                onNavigateToUserAgent = { navController.navigate(Routes.USER_AGENT_SETTINGS) },
                onNavigateToStorageClear = { navController.navigate(Routes.STORAGE_CLEAR) },
                onNavigateToWebVpn = { navController.navigate(Routes.WEBVPN_SETTINGS) },
            )
        }

        // WebVPN 设置页
        animatedComposable(settings = appSettings, route = Routes.WEBVPN_SETTINGS) {
            WebVpnSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 清除存储空间页
        animatedComposable(settings = appSettings, route = Routes.STORAGE_CLEAR) {
            StorageClearScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 浏览器标识设置页
        animatedComposable(settings = appSettings, route = Routes.USER_AGENT_SETTINGS) {
            UserAgentSettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToEdit = { entryId -> navController.navigate(Routes.userAgentEditRoute(entryId)) },
            )
        }

        // 编辑/添加浏览器标识页
        animatedComposable(
            settings = appSettings,
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
        animatedComposable(settings = appSettings, route = Routes.PERSONALIZATION) {
            PersonalizationScreen(
                onBack = { navController.popBackStack() },
                onNavigateToQrCodeSettings = { navController.navigate(Routes.QR_CODE_SETTINGS) },
                isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            )
        }

        // 二维码设置页面
        animatedComposable(settings = appSettings, route = Routes.QR_CODE_SETTINGS) {
            QrCodeSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 意见反馈页面
        // 扫码页面
        animatedComposable(settings = appSettings, route = Routes.SCAN) {
            ScanScreen(
                onBack = { navController.popBackStack() },
                onOpenUrl = { url ->
                    navController.popBackStack()
                    navController.navigate(Routes.unifiedWebViewRoute(url, resources.getString(R.string.scan_title)))
                },
            )
        }

        animatedComposable(settings = appSettings, route = Routes.FEEDBACK) {
            FeedbackScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 添加快捷方式页面
        animatedComposable(settings = appSettings, route = Routes.ADD_SHORTCUT) {
            AddShortcutScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // 查找人员页面
        animatedComposable(settings = appSettings, route = Routes.PERSON_SEARCH) {
            PersonSearchScreen(
                onBack = { navController.popBackStack() },
                onReLogin = { navController.navigate(Routes.loginRoute()) },
            )
        }

        // 有话要说 — 咨询区列表
        animatedComposable(settings = appSettings, route = Routes.SPEAK_UP) {
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
            settings = appSettings,
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
            settings = appSettings,
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
    }
}
