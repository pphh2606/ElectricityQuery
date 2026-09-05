package edu.cqwu.electricity.settings.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.theme.ui.LocalNavController

/**
 * 备份与恢复子页：列出各类备份能力（当前仅“备份/恢复设置项”），
 * 未来账号密码 / Cookie 等导出可在此追加条目。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
) {
    val nav = LocalNavController.current
    val topBarColors = currentTopBarColors()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showCookieSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_backup_restore),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    SettingsEntry(
                        icon = Icons.Outlined.Backup,
                        title = stringResource(R.string.settings_backup_settings_item),
                        onClick = { showSettingsSheet = true },
                    )
                    SettingsEntry(
                        icon = Icons.Outlined.Lock,
                        title = stringResource(R.string.settings_cookie_item),
                        onClick = { showCookieSheet = true },
                    )
                }
            }
        }
    }

    // 设置项备份选择弹窗
    BackupRestoreSheet(
        visible = showSettingsSheet,
        onDismiss = { showSettingsSheet = false },
        onExport = {
            showSettingsSheet = false
            nav.navigate(Routes.SETTINGS_BACKUP_EXPORT)
        },
        onImport = {
            showSettingsSheet = false
            nav.navigate(Routes.SETTINGS_BACKUP_IMPORT)
        },
    )
    // Cookie 备份选择弹窗
    BackupRestoreSheet(
        visible = showCookieSheet,
        onDismiss = { showCookieSheet = false },
        onExport = {
            showCookieSheet = false
            nav.navigate(Routes.SETTINGS_COOKIE_EXPORT)
        },
        onImport = {
            showCookieSheet = false
            nav.navigate(Routes.SETTINGS_COOKIE_IMPORT)
        },
        exportLabelRes = R.string.settings_cookie_sheet_export,
        importLabelRes = R.string.settings_cookie_sheet_import,
    )
}
