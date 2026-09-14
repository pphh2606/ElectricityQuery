package edu.cqwu.electricity.cardcenter

import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
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
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.cardCenterGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 卡中心
    animatedComposable(settings = settings, route = Routes.CARD_CENTER) {
        CardCenterScreen(
            onBack = { navController.popBackStack() },
            onNavigateToQrCode = { type -> navController.navigate(Routes.qrCodeRoute(type)) },
            onNavigateToCardRecharge = { navController.navigate(Routes.CARD_RECHARGE) { launchSingleTop = true } },
        )
    }

    // 学生绑定银行卡
    animatedComposable(settings = settings, route = Routes.BANK_CARD_BIND) {
        val bankCardBindViewModel: BankCardBindViewModel = viewModel()
        BankCardBindScreen(
            viewModel = bankCardBindViewModel,
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }

    // 校园卡充值 — 学号输入+金额选择（ViewModel 作用域 = 本页面：退出即销毁，下次进入重新加载）
    animatedComposable(settings = settings, route = Routes.CARD_RECHARGE) {
        val cardRechargeViewModel: CardRechargeViewModel = viewModel()
        CardRechargeScreen(
            viewModel = cardRechargeViewModel,
            onBack = { navController.popBackStack() },
            onNavigateToPayment = { navController.navigate(Routes.CARD_PAYMENT) { launchSingleTop = true } },
        )
    }

    // 校园卡充值 — 支付执行（复用充值页的 ViewModel：充值页仍在返回栈上，进子页不丢状态）
    animatedComposable(settings = settings, route = Routes.CARD_PAYMENT) { entry ->
        val owner = remember(entry) { navController.getBackStackEntry(Routes.CARD_RECHARGE) }
        val cardRechargeViewModel: CardRechargeViewModel = viewModel(viewModelStoreOwner = owner)
        CardPaymentScreen(
            viewModel = cardRechargeViewModel,
            onBack = { navController.popBackStack() },
        )
    }

    // 账户信息
    animatedComposable(settings = settings, route = Routes.ACCOUNT_INFO) {
        AccountInfoScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }

    // 卡挂失
    animatedComposable(settings = settings, route = Routes.CARD_LOST) {
        CardLostScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }

    // 账单
    animatedComposable(settings = settings, route = Routes.BILL) {
        val billViewModel: BillViewModel = viewModel()
        BillScreen(
            viewModel = billViewModel,
            onBack = { navController.popBackStack() },
            onNavigateToWebView = { url, title -> navController.navigate(Routes.unifiedWebViewRoute(url, title)) },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }
}
