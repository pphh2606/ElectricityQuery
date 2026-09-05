package edu.cqwu.electricity.accountmanagerv2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.LogoutApi
import edu.cqwu.electricity.login.data.SessionManager
import edu.cqwu.electricity.common.net.SessionValidationResult
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.common.ui.AppScaledAlertDialog
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.util.ToastUtils
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
 * - 「修改用户名 / 修改密码」入口，跳转对应修改页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagerScreen(
    onBack: () -> Unit,
    onNavigateToLogin: (accountId: String) -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToUserNameEdit: () -> Unit,
    onNavigateToPasswordEdit: () -> Unit,
    onNavigateToDeviceSession: () -> Unit,
    onNavigateToLoginLog: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarController.current

    var accounts by remember { mutableStateOf(SessionCoordinatorV2.allAccounts()) }
    var activeAccount by remember { mutableStateOf(SessionCoordinatorV2.currentAccount()) }
    var isEditMode by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<String?>(null) }
    var pendingReLoginId by remember { mutableStateOf<String?>(null) }
    var isSwitching by remember { mutableStateOf(false) }

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
                SessionManager.validateCookie(account.cookies)
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
                            context.getString(R.string.account_manager_switch_success, account.username),
                            ToastUtils.Type.SUCCESS,
                        )
                    } else {
                        snackbar.show(
                            context.getString(R.string.account_manager_switch_failed),
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
                        context.getString(R.string.account_manager_switch_network_error),
                        ToastUtils.Type.ERROR,
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_account_manager), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Close else Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.common_edit),
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ── 账号切换卡片 ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    // 所有条目均以副标题「登录时间」显示（区分同一登录用户名的多个登录条目）
                    val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    accounts.forEachIndexed { index, account ->
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
                        if (index < accounts.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }

                    // 添加账号
                    AddAccountRow(
                        enabled = !isEditMode,
                        onClick = onNavigateToAddAccount,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 修改用户名 / 修改密码 ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    PlaceholderRow(
                        icon = Icons.Outlined.Person,
                        title = stringResource(R.string.account_manager_v2_username),
                        onClick = onNavigateToUserNameEdit,
                    )
                    PlaceholderRow(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.account_manager_v2_password),
                        onClick = onNavigateToPasswordEdit,
                    )
                    PlaceholderRow(
                        icon = Icons.Outlined.Devices,
                        title = stringResource(R.string.device_session_title),
                        onClick = onNavigateToDeviceSession,
                    )
                    PlaceholderRow(
                        icon = Icons.Outlined.History,
                        title = stringResource(R.string.login_log_title),
                        onClick = onNavigateToLoginLog,
                    )
                }
            }
        }
    }

    // 切换账号验证加载弹窗（阻断交互）
    if (isSwitching) {
        LoadingDialog(message = stringResource(R.string.account_manager_switching))
    }

    // 删除账号确认弹窗
    if (accountToDelete != null) {
        val accountId = accountToDelete!!
        val deletingUsername = SessionCoordinatorV2.accountById(accountId)?.username
        AppScaledAlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text(text = stringResource(R.string.login_delete_account_title)) },
            text = { Text(text = stringResource(R.string.login_delete_account_confirm, deletingUsername ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val account = SessionCoordinatorV2.accountById(accountId)
                        withContext(Dispatchers.IO) {
                            // 先调用服务端退出登录注销该账号会话（302 视为成功），
                            // 登出失败不阻塞本地删除（尽力而为，API 内部已记录日志）
                            LogoutApi.logout(account?.username ?: "", account?.cookies ?: emptyMap())
                            // 删除账号条目；删当前激活条目时内部清空系统登录态回到未登录
                            SessionCoordinatorV2.delete(accountId)
                        }
                        accountToDelete = null
                        refresh()
                    }
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    // 登录失效安全提醒弹窗（仿新版 MIUI 权限弹窗：拖动手柄 + 按钮上下排列）
    // 使用 BottomSheetDialogV2 统一弹窗形式（内容自适应 + 标题/空白随内容滚动）
    BottomSheetDialogV2(
        visible = pendingReLoginId != null,
        onDismissRequest = { pendingReLoginId = null },
        title = stringResource(R.string.account_manager_v2_relogin_title),
        icon = Icons.Outlined.WarningAmber,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.account_manager_v2_relogin_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { pendingReLoginId = null },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = {
                    val targetId = pendingReLoginId ?: return@Button
                    pendingReLoginId = null
                    onNavigateToLogin(targetId)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(R.string.common_confirm),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * 账号行：圆形头像 + 登录用户名 + trailing 区域。
 * 当前账号高亮 + ✓；编辑模式下显示删除图标（40dp 固定交互区，行高不变化）。
 */
@Composable
private fun AccountRow(
    username: String,
    subtitle: String? = null,
    isActive: Boolean,
    showDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onDelete)
                    .padding(8.dp),
            )
        } else if (isActive) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** 添加账号行 */
@Composable
private fun AddAccountRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.account_manager_add),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 功能行（修改用户名 / 修改密码）：onClick 为空时占位禁用样式 */
@Composable
private fun PlaceholderRow(
    icon: ImageVector,
    title: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.alpha(0.38f))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
