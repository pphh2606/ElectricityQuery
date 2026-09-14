package edu.cqwu.electricity.accountmanagerv2

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.accountManagerGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 账号管理页（独立页面，代码位于 accountmanagerv2 包）
    animatedComposable(settings = settings, route = Routes.ACCOUNT_MANAGER) {
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
    animatedComposable(settings = settings, route = Routes.USER_NAME_EDIT) {
        UserNameEditScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }

    // 修改密码页（本地化 CAS mobilePasswordChange.do）
    animatedComposable(settings = settings, route = Routes.PASSWORD_CHANGE) {
        PasswordChangeScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }

    // 登录设备管理页（本地化 CAS userOnline.do）
    animatedComposable(settings = settings, route = Routes.DEVICE_SESSION) {
        DeviceSessionScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }

    // 日志记录页（本地化 CAS userLogs.do）
    animatedComposable(settings = settings, route = Routes.LOGIN_LOG) {
        LoginLogScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }
}
