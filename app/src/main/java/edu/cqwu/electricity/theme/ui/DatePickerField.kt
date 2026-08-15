package edu.cqwu.electricity.theme.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日期选择器输入框 — 只读，点击尾部日历图标弹出 Material 3 原生 [DatePickerDialog]。
 *
 * @param label 输入框标签
 * @param value 当前日期字符串（格式 yyyy-MM-dd）
 * @param onValueChanged 选择新日期后的回调
 * @param modifier 外部修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val appDensity = LocalDensity.current
    val datePickerState = rememberDatePickerState()

    // 打开弹窗时同步当前值到 DatePicker
    LaunchedEffect(showDialog, value) {
        if (showDialog && value.isNotBlank()) {
            try {
                val millis = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .parse(value)?.time
                datePickerState.selectedDateMillis = millis
            } catch (_: Exception) {
                datePickerState.selectedDateMillis = null
            }
        } else if (showDialog) {
            datePickerState.selectedDateMillis = null
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.CalendarToday,
                    contentDescription = stringResource(R.string.common_select_date),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
    )

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                ProvideAppScaledDensity(appDensity) {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                                timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
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
