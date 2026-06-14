package edu.cqwu.electricity.ui.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.network.AccountManager
import edu.cqwu.electricity.data.network.AutoSwitchResult
import edu.cqwu.electricity.data.network.CookieStore
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.BottomSheetItem
import edu.cqwu.electricity.ui.components.LoadingDialog
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.launch

/**
 * 账号管理底部弹窗
 *
 * 交互流程：
 * - 点击任意账号（含当前）→ 自动验证 Cookie → 有效则切换 / 无效则跳转手动登录
 * - 右上角编辑按钮 → 进入编辑模式，账号右侧显示删除图标
 * - 点击「添加账号」→ 跳转空白 LoginScreen
 *
 * @param show 是否显示弹窗
 * @param onDismiss 关闭弹窗回调
 * @param onNavigateToLogin 跳转登录页回调
 * @param onSwitchSuccess 切换账号成功回调（用于刷新上层页面数据）
 */
@Composable
fun AccountManagerSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onSwitchSuccess: () -> Unit,
) {
    if (!show) return

    val context = LocalContext.current
    val accountStore = remember { AccountStore(context) }
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    // 账号列表刷新触发器
    var refreshTrigger by remember { mutableStateOf(0) }
    val accounts = remember(refreshTrigger) { accountStore.getAllAccounts() }
    val activeUser = remember(refreshTrigger) { AccountManager.getActiveUser() }

    // 自动登录 loading 状态
    var isAutoLogging by remember { mutableStateOf(false) }

    // 编辑模式
    var isEditMode by remember { mutableStateOf(false) }

    // 待删除确认的账号
    var accountToDelete by remember { mutableStateOf<String?>(null) }

    BottomSheetDialog(
        onDismissRequest = { if (!isAutoLogging) onDismiss() },
        title = stringResource(R.string.account_manager_title),
        sheetGesturesEnabled = !isAutoLogging,
        trailingButton = {
            androidx.compose.material3.IconButton(
                onClick = { isEditMode = !isEditMode }
            ) {
                androidx.compose.material3.Icon(
                    imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = null,
                )
            }
        },
    ) {
        // 账号列表
        accounts.forEach { account ->
            val isActive = account.username == activeUser
            BottomSheetItem(
                icon = if (isActive) Icons.Default.Refresh else Icons.Default.Person,
                title = if (isActive) {
                    "${account.username}（${stringResource(R.string.account_manager_current)}）"
                } else {
                    account.username
                },
                selected = isActive,
                enabled = !isAutoLogging,
                trailingContent = if (isEditMode) {
                    {
                        androidx.compose.material3.IconButton(
                            onClick = { accountToDelete = account.username }
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else null,
                onClick = {
                    if (isEditMode) return@BottomSheetItem
                    isAutoLogging = true
                    scope.launch {
                        when (val result = AccountManager.autoSwitchToUser(account.username, accountStore)) {
                            is AutoSwitchResult.Success -> {
                                isAutoLogging = false
                                refreshTrigger++
                                snackbar.show(
                                    context.getString(R.string.account_manager_switch_success, account.username),
                                    ToastUtils.Type.SUCCESS
                                )
                                onDismiss()
                                onSwitchSuccess()
                            }
                            is AutoSwitchResult.NeedManualLogin -> {
                                isAutoLogging = false
                                onDismiss()
                                onNavigateToLogin()
                            }
                            is AutoSwitchResult.Error -> {
                                isAutoLogging = false
                                onDismiss()
                                onNavigateToLogin()
                            }
                        }
                    }
                }
            )
        }

        // 分隔间距
        Spacer(modifier = Modifier.height(8.dp))

        // 添加账号入口
        BottomSheetItem(
            icon = Icons.Default.PersonAdd,
            title = stringResource(R.string.account_manager_add),
            enabled = !isAutoLogging && !isEditMode,
            onClick = {
                onDismiss()
                onNavigateToLogin()
            }
        )
    }

    // 删除确认弹窗
    DeleteAccountSheet(
        account = accountToDelete,
        onDismiss = { accountToDelete = null },
        onConfirm = {
            val username = accountToDelete ?: return@DeleteAccountSheet
            val isActiveUser = username == AccountManager.getActiveUser()
            accountStore.removeAccount(username)
            if (isActiveUser) {
                CookieStore.removeAllCookies()
            }
            AccountManager.removeUser(username)
            accountToDelete = null
            refreshTrigger++
            onSwitchSuccess()
        }
    )

    // 自动登录 loading 弹窗（叠加在 BottomSheet 之上）
    if (isAutoLogging) {
        LoadingDialog(message = stringResource(R.string.account_manager_auto_login))
    }
}
