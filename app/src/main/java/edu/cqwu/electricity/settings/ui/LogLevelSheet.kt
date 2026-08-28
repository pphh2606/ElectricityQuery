package edu.cqwu.electricity.settings.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.logging.LogLevel
import edu.cqwu.electricity.common.ui.BottomSheetItem
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState

@Composable
fun LogLevelSheet(
    showSheet: Boolean,
    onDismiss: () -> Unit,
) {
    val appSettings = LocalAppSettingsState.current

    BottomSheetDialogV2(
        visible = showSheet,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.log_level_select),
    ) {
        LogLevel.entries.forEach { level ->
            BottomSheetItem(
                icon = null,
                title = stringResource(level.labelRes),
                selected = level == appSettings.logLevel,
                onClick = {
                    appSettings.updateLogLevel(level)
                    onDismiss()
                },
            )
        }
    }
}

@get:StringRes
private val LogLevel.labelRes: Int
    get() = when (this) {
        LogLevel.VERBOSE -> R.string.log_level_verbose
        LogLevel.DEBUG -> R.string.log_level_debug
        LogLevel.INFO -> R.string.log_level_info
        LogLevel.WARN -> R.string.log_level_warn
        LogLevel.ERROR -> R.string.log_level_error
        LogLevel.OFF -> R.string.log_level_off
    }
