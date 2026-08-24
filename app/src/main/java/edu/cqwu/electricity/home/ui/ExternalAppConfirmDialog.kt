package edu.cqwu.electricity.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.ListSheetDialog

/**
 * 外部应用打开确认弹窗。
 *
 * 首页点击与桌面快捷方式共用：自定义 scheme（mamp:// 等）功能打开前
 * 统一弹窗确认，确认后由调用方执行外部 Intent。
 *
 * 布局与账号管理「安全提醒」弹窗一致：拖动手柄 + 居中图标标题 + 居中消息
 * + 上下全宽按钮（取消灰色背景 / 确定主题色）。
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
    ListSheetDialog(
        visible = pending != null,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_external_app_title),
        icon = Icons.Outlined.OpenInBrowser,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            pending?.let { (appName, _) ->
                Text(
                    text = stringResource(R.string.home_external_app_message, appName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = onDismiss,
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
                    pending?.let { (name, url) -> onConfirm(name, url) }
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
