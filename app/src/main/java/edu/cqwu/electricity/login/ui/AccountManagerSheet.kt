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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.AccountStore
import edu.cqwu.electricity.login.data.AccountManager
import edu.cqwu.electricity.login.data.CookieStore
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem

/**
 * 账号管理底部弹窗
 *
 * 交互流程：
 * - 点击已有账号 → 跳转登录页（自动填充该账号信息）
 * - 右上角编辑按钮 → 进入编辑模式，账号右侧显示删除图标
 * - 点击「添加账号」→ 跳转空白 LoginScreen
 */
@Composable
fun AccountManagerSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToAddAccount: () -> Unit = onNavigateToLogin,
    onSwitchSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val accountStore = remember { AccountStore.getInstance(context) }

    // 账号列表刷新触发器
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val accounts = remember(refreshTrigger) { accountStore.getAllAccounts() }
    val activeUser = remember(refreshTrigger) { AccountManager.getActiveUser() }

    // 编辑模式
    var isEditMode by remember { mutableStateOf(false) }

    // 待删除确认的账号
    var accountToDelete by remember { mutableStateOf<String?>(null) }

    BottomSheetDialog(
        visible = show,
        onDismissRequest = { onDismiss() },
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
                    if (isEditMode) return@BottomSheetItem
                    onDismiss()
                    onNavigateToLogin()
                }
            )
        }

        // 分隔间距
        Spacer(modifier = Modifier.height(8.dp))

        // 添加账号入口
        BottomSheetItem(
            icon = Icons.Outlined.PersonAdd,
            title = stringResource(R.string.account_manager_add),
            enabled = !isEditMode,
            onClick = {
                onDismiss()
                onNavigateToAddAccount()
            }
        )
    }

    // 删除确认弹窗
    DeleteAccountSheet(
        visible = accountToDelete != null,
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
}
