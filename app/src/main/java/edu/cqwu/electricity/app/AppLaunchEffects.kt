package edu.cqwu.electricity.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.navigation.NavHostController
import edu.cqwu.electricity.R
import edu.cqwu.electricity.home.data.ExternalAppOpener
import edu.cqwu.electricity.home.data.HomeAppLauncher
import edu.cqwu.electricity.home.ui.ExternalAppConfirmDialog
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.login.data.SessionManager
import edu.cqwu.electricity.login.data.SessionValidationResult
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.settings.data.SettingsKeys
import edu.cqwu.electricity.settings.data.SettingsPreferences
import edu.cqwu.electricity.settings.ui.UpdateFoundSheet
import edu.cqwu.electricity.shortcut.util.ShortcutHelper
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.util.ToastUtils
import edu.cqwu.electricity.update.data.UpdateCheckCoordinator
import edu.cqwu.electricity.update.data.UpdateCheckResult

/** 进程级标记：启动自动更新检查只执行一次 */
private var startupUpdateCheckDone = false

/** 进程级标记：启动 Cookie 静默验证只执行一次 */
private var startupCookieValidationDone = false

/**
 * 应用启动横切逻辑（原散落在 AppShell / NavGraph）：
 * 1. 启动自动更新检查（进程级一次）
 * 2. 启动 Cookie 静默验证（进程级一次）
 * 3. 桌面快捷方式启动分发
 * 4. 外部应用（自定义 scheme）确认弹窗
 *
 * 挂载于 [AppShell]，与 [AppNavGraph] 平级；共享 CompositionLocal 中的
 * LocalSnackbarController / LocalNavController。
 */
@Composable
fun AppLaunchEffects(
    navController: NavHostController,
    shortcutAppInfo: ShortcutHelper.ShortcutAppInfo? = null,
    shortcutLaunchId: Int = 0,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current

    // ── 启动自动更新检查 ──
    val settingsPrefs = remember { SettingsPreferences(context) }
    var autoUpdateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    val updateCheckCoordinator = remember { UpdateCheckCoordinator(context) }
    LaunchedEffect(Unit) {
        if (!startupUpdateCheckDone) {
            startupUpdateCheckDone = true
            if (settingsPrefs.get(SettingsKeys.AUTO_UPDATE_ENABLED)) {
                autoUpdateResult = updateCheckCoordinator.check(respectSkipped = true)
            }
        }
    }
    val foundUpdate = autoUpdateResult as? UpdateCheckResult.Found
    if (foundUpdate != null) {
        UpdateFoundSheet(
            info = foundUpdate.info,
            channel = foundUpdate.channel,
            isSkipped = updateCheckCoordinator.isSkipped(foundUpdate.info),
            onSkipChange = { skipped ->
                updateCheckCoordinator.setSkipped(foundUpdate.info.app.versionCode, skipped)
            },
            onDismiss = { autoUpdateResult = null },
        )
    }

    // ── 启动时后台静默验证当前账号 Cookie 有效性（仅进程启动后执行一次）──
    LaunchedEffect(Unit) {
        if (!startupCookieValidationDone) {
            startupCookieValidationDone = true
            val activeAccount = AccountSessionStore.getActiveAccount()
            val cookies = activeAccount?.cookies ?: emptyMap()
            when (val result = SessionManager.validateCookie(cookies)) {
                is SessionValidationResult.Valid -> {
                    AppLog.d("AppLaunchEffects", "启动 Cookie 验证：有效（${activeAccount?.username}）")
                }
                is SessionValidationResult.Invalid -> {
                    AppLog.d("AppLaunchEffects", "启动 Cookie 验证：失效，跳转登录页")
                    navController.navigate(Routes.COOKIE_EXPIRED_LOGIN)
                }
                is SessionValidationResult.NetworkError -> {
                    AppLog.w("AppLaunchEffects", "启动 Cookie 验证：网络错误 - ${result.message}")
                    snackbar.show(resources.getString(R.string.common_network_error), ToastUtils.Type.ERROR)
                }
            }
        }
    }

    // ── 桌面快捷方式启动分发（与首页点击共用 HomeAppLauncher.launch）──
    var pendingExternalIntent by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(shortcutLaunchId) {
        if (shortcutAppInfo != null) {
            HomeAppLauncher.launch(
                appId = shortcutAppInfo.appId,
                name = shortcutAppInfo.appName,
                openUrl = shortcutAppInfo.openUrl,
                navigate = { navController.navigate(it) },
                onExternal = { url, name -> pendingExternalIntent = name to url },
            )
        }
    }

    // ── 外部应用（自定义 scheme）确认弹窗，与首页共用 ──
    ExternalAppConfirmDialog(
        pending = pendingExternalIntent,
        onDismiss = { pendingExternalIntent = null },
        onConfirm = { name, url ->
            pendingExternalIntent = null
            ExternalAppOpener.open(
                context = context,
                appName = name,
                url = url,
                onFailure = { message ->
                    snackbar.show(message, ToastUtils.Type.ERROR)
                }
            )
        }
    )
}
