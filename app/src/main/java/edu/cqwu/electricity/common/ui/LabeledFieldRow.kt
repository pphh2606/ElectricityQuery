package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 通用"标签 + 值"字段行：小标题在上（可选加粗），完整文本在下（可换行）。
 * 用于登录设备管理展开区、认证日志详情弹窗等详情展示场景。
 *
 * @param label 字段小标题
 * @param value 字段值（完整文本，可换行）
 * @param labelBold 小标题是否加粗（详情弹窗内强调用）
 */
@Composable
fun LabeledFieldRow(
    label: String,
    value: String,
    labelBold: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (labelBold) FontWeight.Bold else null,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
