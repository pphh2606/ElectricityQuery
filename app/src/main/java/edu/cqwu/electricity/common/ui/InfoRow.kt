package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * 通用横向"左标签 — 右值"信息行。
 *
 * 标签在左（onSurfaceVariant），值单行加粗在右（onSurface），
 * 空白值显示 "-"，超长省略。
 *
 * 设计上**不含内边距**，由调用方外层控制间距；如需内边距，通过 [modifier] 传入。
 * （此前 DashboardScreen 与 DetailScreen 各自私有实现，此处统一为标准样式。）
 *
 * @param label 左侧标签
 * @param value 右侧值
 * @param modifier 额外修饰符（如 padding）
 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
