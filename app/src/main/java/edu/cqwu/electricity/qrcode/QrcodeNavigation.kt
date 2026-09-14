package edu.cqwu.electricity.qrcode

import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.common.navigation.QrCodeType
import edu.cqwu.electricity.qrcode.ui.QrCodeDisplayScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.qrcodeGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 二维码显示页面
    animatedComposable(
        settings = settings,
        route = Routes.QR_CODE,
        arguments = listOf(navArgument("qrCodeType") { type = NavType.StringType }),
    ) { backStackEntry ->
        val typeStr = backStackEntry.arguments?.getString("qrCodeType") ?: "PAY"
        val qrCodeType = remember(typeStr) {
            try {
                QrCodeType.valueOf(typeStr)
            } catch (_: Exception) {
                AppLog.w("QrcodeNavigation", "未知二维码类型: $typeStr，回退 PAY")
                QrCodeType.PAY
            }
        }
        val title = when (qrCodeType) {
            QrCodeType.PAY -> stringResource(R.string.card_center_payment_code)
            QrCodeType.BUS -> stringResource(R.string.card_center_transit_code)
        }
        QrCodeDisplayScreen(
            qrCodeType = qrCodeType,
            title = title,
            onBack = { navController.popBackStack() },
        )
    }
}
