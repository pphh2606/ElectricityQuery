package edu.cqwu.electricity.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.BlurPerformanceMode
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 一个毛玻璃参数行：API 名 + 说明 + 数字输入框 + 滑块 + 可调范围。
 *
 * 标题栏模糊（高级页）与弹窗模糊（底部面板）共用同一套控件，保证两处操作方式一致。
 *
 * 输入框单独持有文本状态，允许用户中途打出 `0.` 这类不完整数字；
 * 只有能解析成数字时才提交（并夹到范围内）。
 */
@Composable
internal fun BlurParamRow(
    name: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    decimals: Int,
    onValueChange: (Float) -> Unit,
    horizontalPadding: Dp = 16.dp,
) {
    var text by remember(value, decimals) { mutableStateOf(formatBlurValue(value, decimals)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    input.trim()
                        .replace(',', '.')
                        .toFloatOrNull()
                        ?.let { onValueChange(it.coerceIn(range.start, range.endInclusive)) }
                },
                modifier = Modifier.width(104.dp),
                singleLine = true,
                // 与项目其它轮廓输入框统一：12dp 圆角
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                suffix = if (unit.isEmpty()) null else {
                    { Text(text = unit, style = MaterialTheme.typography.bodySmall) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }

        Slider(
            value = value,
            onValueChange = {
                text = formatBlurValue(it, decimals)
                onValueChange(it)
            },
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(
                R.string.advanced_blur_range_hint,
                formatBlurValue(range.start, decimals),
                formatBlurValue(range.endInclusive, decimals),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * `performanceMode` 的四个档位单选列表。
 *
 * @param current 当前选中档位
 * @param onSelect 选择回调
 */
@Composable
internal fun BlurPerformanceOptions(
    current: BlurPerformanceMode,
    onSelect: (BlurPerformanceMode) -> Unit,
    horizontalPadding: Dp = 16.dp,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "performanceMode",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.advanced_blur_desc_performance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = horizontalPadding),
        )
        Spacer(modifier = Modifier.height(4.dp))
        BlurPerformanceMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(mode) }
                    .padding(horizontal = horizontalPadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = current == mode,
                    onClick = { onSelect(mode) },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(mode.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** 整数位参数（blurRadius）不显示小数，其余保留两位 */
internal fun formatBlurValue(value: Float, decimals: Int): String =
    if (decimals == 0) value.roundToInt().toString()
    else String.format(Locale.US, "%.${decimals}f", value)
