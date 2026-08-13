package edu.cqwu.electricity.settings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.logging.LogLevel
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState

@Composable
fun LogLevelSheet(
    showSheet: Boolean,
    onDismiss: () -> Unit,
) {
    val appSettings = LocalAppSettingsState.current

    BottomSheetDialog(
        visible = showSheet,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.log_level_select),
        fullscreen = false,
        skipPartiallyExpanded = false,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
