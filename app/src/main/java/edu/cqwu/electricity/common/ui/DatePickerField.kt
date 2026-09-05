package edu.cqwu.electricity.common.ui
import edu.cqwu.electricity.logging.AppLog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 日期选择胶囊 — 只读，点击整段文本弹出 Material 3 原生 [DatePickerDialog]。
 *
 * 采用非强调色背景胶囊（surfaceContainerHigh 底色 + 圆形 + 无边框，与 [SectionFilterChip] 观感一致），
 * 供项目各处日期选择框统一复用。
 *
 * @param value 当前日期字符串（格式 yyyy-MM-dd）
 * @param onValueChanged 选择新日期后的回调
 * @param placeholder 空值时的占位文本（如"起始时间"/"结束时间"），为空则值空时留空胶囊
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    var showDialog by remember { mutableStateOf(false) }
    val appDensity = LocalDensity.current
    val datePickerState = rememberDatePickerState(initialDisplayMode = DisplayMode.Input)

    // 打开弹窗时同步当前值到 DatePicker
    LaunchedEffect(showDialog, value) {
        if (showDialog && value.isNotBlank()) {
            try {
                val millis = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .parse(value)?.time
                datePickerState.selectedDateMillis = millis
            } catch (_: Exception) {
                AppLog.w("DatePickerField", "日期解析失败，清空选中")
                datePickerState.selectedDateMillis = null
            }
        } else if (showDialog) {
            datePickerState.selectedDateMillis = null
        }
    }

    val isEmpty = value.isBlank()
    Text(
        text = if (isEmpty) placeholder else value,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (isEmpty) {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                ProvideAppScaledDensity(appDensity) {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter =
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                                    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                                }
                            onValueChanged(formatter.format(Date(millis)))
                        }
                        showDialog = false
                    }) { Text(stringResource(R.string.common_confirm)) }
                }
            },
            dismissButton = {
                ProvideAppScaledDensity(appDensity) {
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        ) {
            ProvideAppScaledDensity(appDensity) {
                DatePicker(state = datePickerState)
            }
        }
    }
}
