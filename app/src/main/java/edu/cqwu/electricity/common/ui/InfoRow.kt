package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 通用横向"左标签 — 右值"信息行（A 类：键值明细行）。
 *
 * 标签在左（onSurfaceVariant），值在右（onSurface / Medium），空白值显示 `-`。
 * 设计上**不含内边距**，由调用方外层控制间距；如需内边距，通过 [modifier] 传入。
 *
 * 两种布局模式：
 * - 默认（`labelWidth = null`）：标签自适应、`SpaceBetween` 撑开——原 DashboardScreen /
 *   DetailScreen 的行为，保持不变；
 * - `labelWidth` 非空：标签固定宽度 + 值占满剩余并**右对齐**——接入者信息、网络服务、
 *   订单详情、我的信息的既有效果。
 *
 * [maxLines] 默认 1（超长省略）；需要完整换行展示长文本（如接入者信息的"展示串"）时传
 * `Int.MAX_VALUE`。
 *
 * 垂直对齐为 `Top`：单行场景与居中视觉一致，多行时标签与首行对齐更自然。
 *
 * @param label 左侧标签
 * @param value 右侧值
 * @param modifier 额外修饰符（如 padding）
 * @param labelWidth 标签固定宽度；null 表示自适应
 * @param maxLines 值最大行数；1 表示单行省略
 */
/** 键值明细行的标准标签宽度：接入者信息、网络服务、订单详情、我的信息统一使用，避免各自写死漂移 */
val InfoLabelWidth: Dp = 104.dp

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp? = null,
    maxLines: Int = 1,
) {
    val fixedLabel = labelWidth != null
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (fixedLabel) {
            Arrangement.spacedBy(12.dp)
        } else {
            Arrangement.SpaceBetween
        },
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (fixedLabel) Modifier.width(labelWidth) else Modifier,
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (fixedLabel) TextAlign.End else TextAlign.Start,
            modifier = if (fixedLabel) Modifier.weight(1f) else Modifier,
        )
    }
}

/**
 * 信息分组标题。
 *
 * 统一为 `titleSmall` + `SemiBold` + `primary`，上下留白 20dp / 4dp，
 * 与「接入者信息」「电表实时状态」等页面的分组标题保持一致。
 */
@Composable
fun InfoSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** 信息行之间的细分隔线：0.5dp + 左缩进 16dp，与字段行左边界对齐 */
@Composable
fun InfoRowDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = 16.dp),
    )
}
