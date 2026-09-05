package edu.cqwu.electricity.settings.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.settings.data.BackupPayloadV2
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.util.ToastUtils
import edu.cqwu.electricity.theme.util.copyToClipboard
import edu.cqwu.electricity.theme.util.restartApp

/** 备份传输模式：导出（只读预览）或导入（可编辑输入） */
enum class BackupTransferModeV2 { EXPORT, IMPORT }

/**
 * 设置备份的导出 / 导入页（通用骨架，未来账号密码 / Cookie 迁移复用同款布局）。
 *
 * 布局：上部内容输入/预览框（导出只读、导入可编辑）+
 * 下方轻量操作按钮（剪贴板 / 本地文件，样式同意见反馈）+
 * 右下角"确定"（样式同修改密码页保存）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupTransferScreen(
    mode: BackupTransferModeV2,
    payload: BackupPayloadV2,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val topBarColors = currentTopBarColors()
    val isExport = mode == BackupTransferModeV2.EXPORT

    val exportText = remember(context) { payload.exportText(context) }
    var importText by remember { mutableStateOf("") }
    var showRestartDialog by remember { mutableStateOf(false) }

    // ── SAF：导出到本地文件 / 从本地文件导入 ──
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write((if (isExport) exportText else importText).toByteArray(Charsets.UTF_8))
            }
            snackbar.show(context.getString(R.string.settings_backup_saved), ToastUtils.Type.SUCCESS)
        }
    }
    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            importText = content
        }
    }

    fun readFromClipboard() {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = manager.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (text.isNullOrBlank()) {
            snackbar.show(context.getString(R.string.common_no_data), ToastUtils.Type.ERROR)
        } else {
            importText = text
        }
    }

    fun doImport() {
        if (payload.importJson(context, importText)) {
            if (payload.restartOnImport) {
                showRestartDialog = true
            } else {
                snackbar.show(context.getString(R.string.settings_cookie_import_ok), ToastUtils.Type.SUCCESS)
            }
        } else {
            snackbar.show(context.getString(R.string.settings_backup_invalid), ToastUtils.Type.ERROR)
        }
    }

    Scaffold(
        // 键盘弹出时让出 IME 高度，避免遮挡底部操作区与"确定"按钮（仅本页生效）
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isExport) payload.exportTitleRes
                            else payload.importTitleRes
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
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
                .padding(16.dp),
        ) {
            // ── 上部：内容区（导出=只读预览；导入=可编辑），大圆角输入框 ──
            OutlinedTextField(
                value = if (isExport) exportText else importText,
                onValueChange = { if (!isExport) importText = it },
                readOnly = isExport,
                label = {
                    Text(
                        text = stringResource(
                            if (isExport) R.string.settings_backup_export_hint
                            else R.string.settings_backup_import_hint
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 轻量操作按钮（同意见反馈"预览/分享日志"样式） ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(
                    onClick = {
                        if (isExport) {
                            copyToClipboard(
                                context, exportText,
                                context.getString(payload.exportTitleRes), snackbar,
                            )
                        } else {
                            readFromClipboard()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isExport) Icons.Outlined.FileDownload else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = stringResource(
                            if (isExport) R.string.settings_backup_export_clipboard
                            else R.string.settings_backup_import_clipboard
                        )
                    )
                }
                TextButton(
                    onClick = {
                        if (isExport) {
                            saveLauncher.launch(payload.fileName)
                        } else {
                            openLauncher.launch(arrayOf("text/*", "application/json", "*/*"))
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (isExport) Icons.Outlined.FileUpload else Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = stringResource(
                            if (isExport) R.string.settings_backup_export_file
                            else R.string.settings_backup_import_file
                        )
                    )
                }
            }

            // ── 右下角"确定"（同修改密码页保存） ──
            Button(
                onClick = { if (isExport) onBack() else doImport() },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp, bottom = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_confirm),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // ── 导入成功 → 提示重启使设置完整生效 ──
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = {
                Text(stringResource(R.string.settings_backup_import_ok))
            },
            confirmButton = {
                TextButton(onClick = { restartApp(context) }) {
                    Text(stringResource(R.string.settings_backup_restart))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(R.string.settings_backup_later))
                }
            },
        )
    }
}
