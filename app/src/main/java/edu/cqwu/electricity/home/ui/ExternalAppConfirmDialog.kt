package edu.cqwu.electricity.home.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.BottomSheetDialog

/**
 * 外部应用打开确认弹窗。
 *
 * 首页点击与桌面快捷方式共用：自定义 scheme（mamp:// 等）功能打开前
 * 统一弹窗确认，确认后由调用方执行外部 Intent。
 *
 * @param pending 待确认项 (appName, url) 对；null 时不显示
 * @param onDismiss 取消
 * @param onConfirm 确认后回调 (appName, url)
 */
@Composable
fun ExternalAppConfirmDialog(
    pending: Pair<String, String>?,
    onDismiss: () -> Unit,
    onConfirm: (appName: String, url: String) -> Unit,
) {
    BottomSheetDialog(
        visible = pending != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_external_app_title),
        icon = Icons.Outlined.OpenInBrowser,
        fullscreen = false,
        leadingButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        trailingButton = {
            TextButton(onClick = {
                pending?.let { (name, url) -> onConfirm(name, url) }
            }) {
                Text(stringResource(R.string.common_confirm))
            }
        }
    ) {
        pending?.let { (appName, _) ->
            Text(
                text = stringResource(R.string.home_external_app_message, appName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
