package edu.cqwu.electricity.login.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.BottomSheetDialog

/**
 * 删除账号确认底部弹窗。
 *
 * @param account 待删除的学号，null 时不渲染
 * @param onDismiss 取消/关闭回调
 * @param onConfirm 确认删除回调
 */
@Composable
fun DeleteAccountSheet(
    visible: Boolean = true,
    account: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.login_delete_account_title),
        icon = Icons.Outlined.Delete,
        leadingButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        trailingButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.common_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        account?.let { username ->
            Text(
                text = stringResource(R.string.login_delete_account_confirm, username),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
