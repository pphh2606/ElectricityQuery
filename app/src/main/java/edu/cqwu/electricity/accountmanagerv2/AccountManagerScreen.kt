@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package edu.cqwu.electricity.accountmanagerv2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.LogoutApi
import edu.cqwu.electricity.login.data.SessionManager
import edu.cqwu.electricity.common.net.SessionValidationResult
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.common.ui.CollapsibleScaffold
import edu.cqwu.electricity.settings.ui.SettingsGroup
import edu.cqwu.electricity.settings.ui.SettingsListItem
import edu.cqwu.electricity.settings.ui.SettingsNavigationRow
import edu.cqwu.electricity.common.ui.ConfirmBottomSheet
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 账号管理页（AccountManagerV2）— 独立于账号管理弹窗，由设置页入口进入。
 *
 * 交互流程：
 * - 点击账号（非当前、非编辑模式）：
 *   - 无登录状态 → 跳登录页预填该账号
 *   - 有登录状态 → 网络验证 CAS 登录态：有效则原子切换；无效则跳登录页；网络错误提示
 * - 右上角编辑图标 → 编辑模式，账号行右侧显示删除图标
 * - 删除账号 → 确认弹窗 → 退出登录 API（预留）+ 删除持久化记录；删当前账号时清空系统登录态回到未登录
 * - 点击「添加账号」→ 空白登录页
 * - 功能卡片一分为二：「修改用户名 / 修改密码 / 手机绑定 / 邮箱绑定」与
 *   「登录设备管理 / 日志记录 / 高级设置」
 * - 「手机绑定 / 邮箱绑定 / 高级设置」走通用内置浏览器，其余跳本地页面
 */

/** 绑定与高级设置入口（CAS 页面，用内置浏览器打开） */
private const val PHONE_BIND_URL = "https://authserver.cqwu.edu.cn/authserver/mobileBindView.do"
private const val EMAIL_BIND_URL = "https://authserver.cqwu.edu.cn/authserver/mailBindView.do"
private const val ADVANCED_SETTING_URL = "https://authserver.cqwu.edu.cn/authserver/userSetting.do"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerScreen(
    onBack: () -> Unit,
    onNavigateToLogin: (accountId: String) -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToUserNameEdit: () -> Unit,
    onNavigateToPasswordEdit: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onNavigateToDeviceSession: () -> Unit,
    onNavigateToLoginLog: () -> Unit,
) {
    val resources = LocalResources.current
    val currentLocale = ConfigurationCompat.getLocales(LocalConfiguration.current).let {
        if (it.isEmpty) Locale.ROOT else it[0]!!
    }
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarController.current
    val phoneBindTitle = stringResource(R.string.account_manager_v2_phone_bind)
    val emailBindTitle = stringResource(R.string.account_manager_v2_email_bind)
    val advancedSettingTitle = stringResource(R.string.account_manager_v2_advanced_setting)

    var accounts by remember { mutableStateOf(SessionCoordinatorV2.allAccounts()) }
    var activeAccount by remember { mutableStateOf(SessionCoordinatorV2.currentAccount()) }
    var isEditMode by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<String?>(null) }
    var pendingReLoginId by remember { mutableStateOf<String?>(null) }
    var isSwitching by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    fun refresh() {
        accounts = SessionCoordinatorV2.allAccounts()
        activeAccount = SessionCoordinatorV2.currentAccount()
    }

    fun switchToAccount(accountId: String) {
        val account = SessionCoordinatorV2.accountById(accountId) ?: return
        if (!account.hasLoginState) {
            // 无登录状态 → 直接进登录页预填该条目
            onNavigateToLogin(account.id)
            return
        }
        isSwitching = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                // 传 account.id：此刻激活的还是旧账号，学号必须回填到这份 cookies 所属的条目上
                SessionManager.validateCookie(account.cookies, account.id)
            }
            when (result) {
                is SessionValidationResult.Valid -> {
                    var activated = false
                    withContext(Dispatchers.IO) {
                        try {
                            SessionCoordinatorV2.activate(account.id)
                            activated = true
                        } catch (e: Exception) {
                            AppLog.w("AccountManagerScreen", "切换账号失败", e)
                        }
                    }
                    isSwitching = false
                    if (activated) {
                        refresh()
                        snackbar.show(
                            resources.getString(R.string.account_manager_switch_success, account.username),
                            ToastUtils.Type.SUCCESS,
                        )
                    } else {
                        snackbar.show(
                            resources.getString(R.string.account_manager_switch_failed),
                            ToastUtils.Type.ERROR,
                        )
                    }
                }
                is SessionValidationResult.Invalid -> {
                    isSwitching = false
                    // 登录态已失效 → 弹出安全提醒，确认后进入该条目登录页
                    pendingReLoginId = account.id
                }
                is SessionValidationResult.NetworkError -> {
                    isSwitching = false
                    snackbar.show(
                        resources.getString(R.string.account_manager_switch_network_error),
                        ToastUtils.Type.ERROR,
                    )
                }
            }
        }
    }

    CollapsibleScaffold(
        title = stringResource(R.string.settings_account_manager),
        onBack = onBack,
        actions = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Close else Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.common_edit),
                        )
                    }
        },
    ) { topInset, scrollModifier ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(scrollModifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(topInset + 8.dp))
            // ── 账号切换卡片 ──
            SettingsGroup {
                // 所有条目均以副标题「登录时间」显示（区分同一登录用户名的多个登录条目）
                val timeFormat = SimpleDateFormat("MM-dd HH:mm", currentLocale)
                accounts.forEach { account ->
                    item {
                        AccountRow(
                            username = account.username,
                            subtitle = stringResource(
                                R.string.account_manager_v2_login_time,
                                timeFormat.format(account.lastLoginTime)
                            ),
                            isActive = account.id == activeAccount?.id,
                            showDelete = isEditMode,
                            onClick = {
                                if (!isEditMode && account.id != activeAccount?.id) {
                                    switchToAccount(account.id)
                                }
                            },
                            onDelete = { accountToDelete = account.id },
                        )
                    }
                }
                item {
                    AddAccountRow(
                        enabled = !isEditMode,
                        onClick = onNavigateToAddAccount,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 修改用户名 / 修改密码 / 手机绑定 / 邮箱绑定 ──
            SettingsGroup {
                item {
                    PlaceholderRow(
                        icon = Icons.Outlined.Person,
                        title = stringResource(R.string.account_manager_v2_username),
                        onClick = onNavigateToUserNameEdit,
                    )
                }
                item {
                    PlaceholderRow(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.account_manager_v2_password),
                        onClick = onNavigateToPasswordEdit,
                    )
                }
                item {
                    PlaceholderRow(
                        icon = Icons.Outlined.PhoneAndroid,
                        title = phoneBindTitle,
                        onClick = { onNavigateToWebView(PHONE_BIND_URL, phoneBindTitle) },
                    )
                }
                item {
                    PlaceholderRow(
                        icon = Icons.Outlined.Email,
                        title = emailBindTitle,
                        onClick = { onNavigateToWebView(EMAIL_BIND_URL, emailBindTitle) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 登录设备管理 / 日志记录 / 高级设置 ──
            SettingsGroup {
                item {
                    PlaceholderRow(
                        icon = Icons.Outlined.Devices,
                        title = stringResource(R.string.device_session_title),
                        onClick = onNavigateToDeviceSession,
                    )
                }
                item {
                    PlaceholderRow(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.login_log_title),
                        onClick = onNavigateToLoginLog,
                    )
                }
                item {
                    PlaceholderRow(
                        icon = Icons.Outlined.Settings,
                        title = advancedSettingTitle,
                        onClick = { onNavigateToWebView(ADVANCED_SETTING_URL, advancedSettingTitle) },
                    )
                }
            }
        }
    }

    // 切换账号验证加载弹窗（阻断交互）
    if (isSwitching) {
        LoadingDialog(message = stringResource(R.string.account_manager_switching))
    }

    // 删除账号加载弹窗（服务端退出登录 + 本地删除）
    if (isDeleting) {
        LoadingDialog(message = stringResource(R.string.account_manager_logging_out))
    }

    // 删除账号确认弹窗
    val deletingAccountId = accountToDelete
    ConfirmBottomSheet(
        visible = deletingAccountId != null,
        onDismissRequest = { accountToDelete = null },
        title = stringResource(R.string.login_delete_account_title),
        message = stringResource(
            R.string.login_delete_account_confirm,
            deletingAccountId?.let { SessionCoordinatorV2.accountById(it)?.username } ?: "",
        ),
        confirmText = stringResource(R.string.common_delete),
        onConfirm = {
            val accountId = deletingAccountId ?: return@ConfirmBottomSheet
            // 先收起确认弹窗，再显示加载弹窗：服务端退出登录要走网络，两个弹窗不叠在一起
            accountToDelete = null
            isDeleting = true
            scope.launch {
                try {
                    val account = SessionCoordinatorV2.accountById(accountId)
                    withContext(Dispatchers.IO) {
                        // 先调用服务端退出登录注销该账号会话（302 视为成功），
                        // 登出失败不阻塞本地删除（尽力而为，API 内部已记录日志）
                        LogoutApi.logout(account?.username ?: "", account?.cookies ?: emptyMap())
                        // 删除账号条目；删当前激活条目时内部清空系统登录态回到未登录
                        SessionCoordinatorV2.delete(accountId)
                    }
                } finally {
                    // 成功/失败/取消都要收起加载弹窗，避免一直停在「退出登录中…」
                    isDeleting = false
                }
                refresh()
            }
        },
    )

    // 登录失效安全提醒弹窗（手柄 + 图标标题 + 居中说明 + 上下全宽按钮）
    ConfirmBottomSheet(
        visible = pendingReLoginId != null,
        onDismissRequest = { pendingReLoginId = null },
        title = stringResource(R.string.account_manager_v2_relogin_title),
        message = stringResource(R.string.account_manager_v2_relogin_message),
        icon = Icons.Outlined.WarningAmber,
        confirmText = stringResource(R.string.common_confirm),
        onConfirm = {
            val targetId = pendingReLoginId ?: return@ConfirmBottomSheet
            pendingReLoginId = null
            onNavigateToLogin(targetId)
        },
    )
}

/** 账号行：头像 + 用户名 + 登录时间；编辑模式行尾换成删除按钮 */
@Composable
private fun AccountRow(
    username: String,
    subtitle: String? = null,
    isActive: Boolean,
    showDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val supportingBlock: (@Composable () -> Unit)? = subtitle?.let { text ->
        {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SettingsListItem(
        title = username,
        onClick = onClick,
        titleColor = if (isActive) MaterialTheme.colorScheme.primary else null,
        leading = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        supporting = supportingBlock,
        // trailing 三态统一占 48dp —— 即 IconButton 的实际布局尺寸，三态等宽等高，
        // 切换编辑模式时行高不变。
        trailing = {
            if (showDelete) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (isActive) {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        },
    )
}

/** 添加账号行 */
@Composable
private fun AddAccountRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SettingsListItem(
        title = stringResource(R.string.account_manager_add),
        onClick = onClick,
        enabled = enabled,
        icon = Icons.Outlined.Add,
    )
}

/** 功能行（修改用户名 / 修改密码）：onClick 为空时占位禁用样式 */
@Composable
private fun PlaceholderRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        SettingsNavigationRow(icon = icon, title = title, onClick = onClick)
    } else {
        SettingsListItem(icon = icon, title = title, onClick = null, alpha = 0.38f)
    }
}
