package edu.cqwu.electricity.theme.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.settings.data.AppLanguage
import edu.cqwu.electricity.settings.data.SettingsPreferences

/**
 * 语言切换按钮（图标 + 弹窗一体的便捷组件）。
 *
 * 点击语言图标后弹出 [LanguageSwitchSheet] 底部弹窗，
 * 选择语言后持久化偏好并重启 Activity。
 *
 * 适用于登录页等只需一个图标的场景。
 */
@Composable
fun LanguageSwitchButton() {
    var showSheet by remember { mutableStateOf(false) }

    IconButton(onClick = { showSheet = true }) {
        Icon(
            imageVector = Icons.Outlined.Translate,
            contentDescription = stringResource(R.string.language_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    LanguageSwitchSheet(showSheet = showSheet, onDismiss = { showSheet = false })
}

/**
 * 语言选择底部弹窗（不含触发按钮）。
 *
 * 接收外部的 [showSheet] / [onDismiss] 控制显隐，
 * 内部自动读取当前语言、展示选项列表、持久化偏好并重启 Activity。
 *
 * 适用于已有自定义触发控件的页面（如 ConfigScreen 的 ConfigEntry 行）。
 */
@Composable
fun LanguageSwitchSheet(
    showSheet: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val settingsPrefs = remember { SettingsPreferences(context) }
    val currentLanguage by remember { mutableStateOf(settingsPrefs.getAppLanguage()) }

    BottomSheetDialog(
        visible = showSheet,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.language_select),
        fullscreen = false,
        skipPartiallyExpanded = false,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AppLanguage.entries.forEach { language ->
                val title = if (language == AppLanguage.SYSTEM) {
                    stringResource(R.string.language_system)
                } else {
                        stringResource(language.labelRes)
                }
                BottomSheetItem(
                    icon = null,
                    title = title,
                    selected = language == currentLanguage,
                    onClick = {
                        val activity = context as? Activity ?: return@BottomSheetItem
                        onDismiss()
                        settingsPrefs.setAppLanguage(language)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            val intent = Intent(activity, activity.javaClass)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                            activity.startActivity(intent)
                            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                            activity.finish()
                        }
                    },
                )
            }
        }
    }
}
