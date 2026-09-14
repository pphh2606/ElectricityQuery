package edu.cqwu.electricity.settings

import android.os.Build
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.settings.data.CookiesBackupPayloadV2
import edu.cqwu.electricity.settings.data.SettingsBackupPayloadV2
import edu.cqwu.electricity.settings.ui.AboutScreen
import edu.cqwu.electricity.settings.ui.BackupRestoreScreen
import edu.cqwu.electricity.settings.ui.BackupTransferModeV2
import edu.cqwu.electricity.settings.ui.BackupTransferScreen
import edu.cqwu.electricity.settings.ui.ConfigScreen
import edu.cqwu.electricity.settings.ui.PersonalizationScreen
import edu.cqwu.electricity.settings.ui.QrCodeSettingsScreen
import edu.cqwu.electricity.settings.ui.SettingsScreen
import edu.cqwu.electricity.settings.ui.StorageClearScreen
import edu.cqwu.electricity.settings.ui.UserAgentEditScreen
import edu.cqwu.electricity.settings.ui.UserAgentSettingsScreen
import edu.cqwu.electricity.settings.ui.WebVpnSettingsScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.settingsGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 设置页面
    animatedComposable(settings = settings, route = Routes.SETTINGS) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 关于页面
    animatedComposable(settings = settings, route = Routes.ABOUT) {
        AboutScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 备份与恢复子页
    animatedComposable(settings = settings, route = Routes.SETTINGS_BACKUP_RESTORE) {
        BackupRestoreScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 设置备份：导出
    animatedComposable(settings = settings, route = Routes.SETTINGS_BACKUP_EXPORT) {
        BackupTransferScreen(
            mode = BackupTransferModeV2.EXPORT,
            payload = SettingsBackupPayloadV2,
            onBack = { navController.popBackStack() },
        )
    }

    // 设置备份：导入
    animatedComposable(settings = settings, route = Routes.SETTINGS_BACKUP_IMPORT) {
        BackupTransferScreen(
            mode = BackupTransferModeV2.IMPORT,
            payload = SettingsBackupPayloadV2,
            onBack = { navController.popBackStack() },
        )
    }

    // Cookie 备份：导出
    animatedComposable(settings = settings, route = Routes.SETTINGS_COOKIE_EXPORT) {
        BackupTransferScreen(
            mode = BackupTransferModeV2.EXPORT,
            payload = CookiesBackupPayloadV2,
            onBack = { navController.popBackStack() },
        )
    }

    // Cookie 备份：导入
    animatedComposable(settings = settings, route = Routes.SETTINGS_COOKIE_IMPORT) {
        BackupTransferScreen(
            mode = BackupTransferModeV2.IMPORT,
            payload = CookiesBackupPayloadV2,
            onBack = { navController.popBackStack() },
        )
    }

    // 配置页
    animatedComposable(settings = settings, route = Routes.CONFIG) {
        ConfigScreen(
            onBack = { navController.popBackStack() },
            onNavigateToUserAgent = { navController.navigate(Routes.USER_AGENT_SETTINGS) },
            onNavigateToStorageClear = { navController.navigate(Routes.STORAGE_CLEAR) },
            onNavigateToWebVpn = { navController.navigate(Routes.WEBVPN_SETTINGS) },
        )
    }

    // WebVPN 设置页
    animatedComposable(settings = settings, route = Routes.WEBVPN_SETTINGS) {
        WebVpnSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 清除存储空间页
    animatedComposable(settings = settings, route = Routes.STORAGE_CLEAR) {
        StorageClearScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 浏览器标识设置页
    animatedComposable(settings = settings, route = Routes.USER_AGENT_SETTINGS) {
        UserAgentSettingsScreen(
            onBack = { navController.popBackStack() },
            onNavigateToEdit = { entryId -> navController.navigate(Routes.userAgentEditRoute(entryId)) },
        )
    }

    // 编辑/添加浏览器标识页
    animatedComposable(
        settings = settings,
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
    animatedComposable(settings = settings, route = Routes.PERSONALIZATION) {
        PersonalizationScreen(
            onBack = { navController.popBackStack() },
            onNavigateToQrCodeSettings = { navController.navigate(Routes.QR_CODE_SETTINGS) },
            isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    }

    // 二维码设置页面
    animatedComposable(settings = settings, route = Routes.QR_CODE_SETTINGS) {
        QrCodeSettingsScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
