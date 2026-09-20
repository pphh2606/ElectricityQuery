package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R

/**
 * 通用确认底部弹窗：拖动手柄 + 居中标题（可选图标）+ 居中说明 + 上下全宽按钮。
 *
 * 全应用的危险操作确认（挂失、删除、解绑、断开、重启等）统一走这里：
 * 既省掉每处重复书写按钮样式，也保证「取消灰底 / 确认主题色」不会再被写错。
 *
 * @param visible 控制显示
 * @param onDismissRequest 点击空白、下拉手势或点「取消」时回调
 * @param title 标题，显示在手柄下方居中
 * @param confirmText 确认按钮文案
 * @param onConfirm 确认按钮回调。是否关闭弹窗由调用方决定（支持先异步请求再关）
 * @param message 说明文字，null 时不显示（用于只有标题的极简确认）
 * @param icon 标题上方图标，null 时不显示
 * @param cancelText 取消按钮文案，null 时用通用的「取消」
 */
@Composable
fun ConfirmBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    message: String? = null,
    icon: ImageVector? = null,
    cancelText: String? = null,
) {
    BottomSheetDialogV2(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = title,
        icon = icon,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(cancelText ?: stringResource(R.string.common_cancel), fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
