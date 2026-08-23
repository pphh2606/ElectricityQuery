package edu.cqwu.electricity.login.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.login.data.LogoutApi
import edu.cqwu.electricity.login.data.SessionManager
import edu.cqwu.electricity.login.data.SessionValidationResult
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LoadingDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 账号管理底部弹窗（QQ 模式多账号切换）。
 *
 * 交互流程：
 * - 点击已有账号：
 *   - 该账号有持久化登录状态（cookie）→ 网络验证 CAS 登录态是否有效：
 *     - 有效 → 原子切换所有登录态到该账号（清除旧登录态，无需重新登录）
 *     - 无效 → 跳转登录页预填该账号，手动登录
 *     - 网络错误 → 提示，不切换、不动当前登录态
 *   - 该账号无登录状态 → 直接跳转登录页预填该账号
 * - 右上角编辑按钮 → 进入编辑模式，账号右侧显示删除图标
 * - 删除账号：调用退出登录 API（预留）+ 删除该账号的学号/密码/登录状态；
 *   删除的是当前激活账号时清空系统登录态，回到未登录
 * - 点击「添加账号」→ 跳转空白 LoginScreen
 */
@Composable
fun AccountManagerSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onNavigateToLogin: (username: String) -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onSwitchSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarController.current

    // 账号列表刷新触发器
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val accounts = remember(refreshTrigger) { AccountSessionStore.getAllAccounts() }
    val activeUser = remember(refreshTrigger) { AccountSessionStore.getActiveUser() }

    // 编辑模式
    var isEditMode by remember { mutableStateOf(false) }

    // 待删除确认的账号
    var accountToDelete by remember { mutableStateOf<String?>(null) }

    // 正在验证登录态/切换中（加载弹窗，阻断交互）
    var isSwitching by remember { mutableStateOf(false) }

    /**
     * QQ 模式切换：网络验证目标账号登录态，有效则原子切换，无效/无登录态则进登录页。
     * 切换成功之前不改动当前任何登录状态。
     */
    fun switchToAccount(username: String) {
        val account = AccountSessionStore.getAccount(username) ?: return
        if (!account.hasLoginState) {
            // 该账号无登录状态 → 直接进登录页预填
            onDismiss()
            onNavigateToLogin(username)
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
                            AccountSessionStore.activate(username)
                            activated = true
                        } catch (e: Exception) {
                            AppLog.w("AccountManagerSheet", "切换账号失败", e)
                        }
                    }
                    isSwitching = false
                    onDismiss()
                    if (activated) {
                        onSwitchSuccess()
                        snackbar.show(
                            context.getString(R.string.account_manager_switch_success, username),
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
                    onDismiss()
                    // 登录态已失效 → 进入该账号登录页手动登录
                    onNavigateToLogin(username)
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

    BottomSheetDialog(
        visible = show,
        onDismissRequest = { if (!isSwitching) onDismiss() },
        title = stringResource(R.string.account_manager_title),
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
                icon = Icons.Outlined.Person,
                title = if (isActive) {
                    "${account.username}（${stringResource(R.string.account_manager_current)}）"
                } else {
                    account.username
                },
                selected = isActive,
                trailingContent = if (isEditMode) {
                    {
                        androidx.compose.material3.IconButton(
                            onClick = { accountToDelete = account.username }
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else null,
                onClick = {
                    if (isEditMode || isSwitching) return@BottomSheetItem
                    switchToAccount(account.username)
                }
            )
        }

        // 分隔间距
        Spacer(modifier = Modifier.height(8.dp))

        // 添加账号入口
        BottomSheetItem(
            icon = Icons.Outlined.PersonAdd,
            title = stringResource(R.string.account_manager_add),
            enabled = !isEditMode && !isSwitching,
            onClick = {
                onDismiss()
                onNavigateToAddAccount()
            }
        )
    }

    // 切换账号验证加载弹窗
    if (isSwitching) {
        LoadingDialog(
            message = stringResource(R.string.account_manager_switching)
        )
    }

    // 删除确认弹窗
    DeleteAccountSheet(
        visible = accountToDelete != null,
        account = accountToDelete,
        onDismiss = { accountToDelete = null },
        onConfirm = {
            val username = accountToDelete ?: return@DeleteAccountSheet
            scope.launch {
                val account = AccountSessionStore.getAccount(username)
                withContext(Dispatchers.IO) {
                    // 预留退出登录 API 调用点（目前服务端无此接口）
                    LogoutApi.logout(username, account?.cookies ?: emptyMap())
                    // 删除账号（学号 + 密码 + 登录状态）；若是当前激活账号，内部会清空系统登录态回到未登录
                    AccountSessionStore.deleteAccount(username)
                }
                accountToDelete = null
                refreshTrigger++
                onSwitchSuccess()
            }
        }
    )
}
