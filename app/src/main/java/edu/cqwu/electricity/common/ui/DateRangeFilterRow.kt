package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

/**
 * 通用起止日期筛选行：两端无外框胶囊日期 + 中间分隔符，一行水平分布。
 *
 * 两端日期点击弹出原生 [DatePickerDialog]（复用 [DatePickerField]）。
 * 供项目各处"起止日期"筛选统一复用。
 *
 * @param beginLabel 起始日期占位文本（空值时显示在胶囊内）
 * @param endLabel 结束日期占位文本
 * @param beginValue 起始日期（yyyy-MM-dd）
 * @param endValue 结束日期（yyyy-MM-dd）
 * @param onBeginChange 起始日期变化回调
 * @param onEndChange 结束日期变化回调
 * @param modifier 外部修饰符
 */
@Composable
fun DateRangeFilterRow(
    beginLabel: String,
    endLabel: String,
    beginValue: String,
    endValue: String,
    onBeginChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(R.string.common_date_range_separator)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DatePickerField(
            value = beginValue,
            onValueChanged = onBeginChange,
            placeholder = beginLabel,
        )
        Text(
            text = separator,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DatePickerField(
            value = endValue,
            onValueChanged = onEndChange,
            placeholder = endLabel,
        )
    }
}
