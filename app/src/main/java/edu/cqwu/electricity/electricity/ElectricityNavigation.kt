package edu.cqwu.electricity.electricity

import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.common.navigation.DetailType
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
import edu.cqwu.electricity.common.settings.AppSettingsState
import edu.cqwu.electricity.webview.ui.UnifiedWebViewScreen

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.electricityGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 电费主页（充值 Tab 在其中）
    animatedComposable(settings = settings, route = Routes.ELECTRICITY_MAIN) {
        // 三个 ViewModel 都随本页面存活：退出电费主页即销毁，下次进入重新查询
        val electricityViewModel: ElectricityViewModel = viewModel()
        val myRoomViewModel: MyRoomViewModel = viewModel()
        val rechargeViewModel: RechargeViewModel = viewModel()
        ElectricityMainScreen(
            viewModel = electricityViewModel,
            rechargeViewModel = rechargeViewModel,
            myRoomViewModel = myRoomViewModel,
            onBack = { navController.popBackStack() },
        )
    }

    animatedComposable(
        settings = settings,
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
        DetailScreen(
            viewModel = detailViewModel,
            detailType = DetailType.METER_STATUS,
            onBack = { navController.popBackStack() },
        )
    }

    // 电费充值 — 支付选择（复用充值页的 ViewModel：电费主页仍在返回栈上，进子页不丢状态）
    animatedComposable(settings = settings, route = Routes.PAYMENT_SELECTION) { entry ->
        val owner = remember(entry) { navController.getBackStackEntry(Routes.ELECTRICITY_MAIN) }
        val rechargeViewModel: RechargeViewModel = viewModel(viewModelStoreOwner = owner)
        PaymentSelectionScreen(
            viewModel = rechargeViewModel,
            onBack = { navController.popBackStack() },
            onPaymentComplete = { navController.popBackStack(Routes.ELECTRICITY_MAIN, false) },
        )
    }

    // 电费充值记录（同样复用充值页的 ViewModel）
    animatedComposable(
        settings = settings,
        route = Routes.RECHARGE_RECORD,
        arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
    ) { entry ->
        val owner = remember(entry) { navController.getBackStackEntry(Routes.ELECTRICITY_MAIN) }
        val rechargeViewModel: RechargeViewModel = viewModel(viewModelStoreOwner = owner)
        RechargeRecordScreen(
            viewModel = rechargeViewModel,
            roomId = entry.arguments?.getString("roomId") ?: "",
            onBack = { navController.popBackStack() }
        )
    }

    animatedComposable(
        settings = settings,
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
        settings = settings,
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
    animatedComposable(settings = settings, route = Routes.RECHARGE_H5_WEBVIEW) {
        UnifiedWebViewScreen(
            url = Routes.H5_RECHARGE_URL,
            onClose = { navController.popBackStack(Routes.ELECTRICITY_MAIN, false) },
        )
    }
}
