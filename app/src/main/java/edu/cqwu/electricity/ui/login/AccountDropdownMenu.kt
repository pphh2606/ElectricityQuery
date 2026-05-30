package edu.cqwu.electricity.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

/**
 * 学号下拉选择菜单（类似 QQ 样式：账号居左，删除按钮居右）。
 *
 * @param savedAccounts 已保存的学号列表
 * @param expanded 菜单展开状态
 * @param onDismiss 关闭菜单回调
 * @param onSelectAccount 点击账号回调（通常触发 switchToUser）
 * @param onDeleteAccount 点击删除回调（通常设置 deleteConfirmAccount）
 */
@Composable
fun AccountDropdownMenu(
    savedAccounts: List<String>,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelectAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded && savedAccounts.isNotEmpty(),
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        savedAccounts.forEach { account ->
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = account,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onDeleteAccount(account) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_delete_account),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                onClick = {
                    onDismiss()
                    onSelectAccount(account)
                }
            )
        }
    }
}
