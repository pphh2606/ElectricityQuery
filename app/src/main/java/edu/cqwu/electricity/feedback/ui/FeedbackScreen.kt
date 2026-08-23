package edu.cqwu.electricity.feedback.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.LoadingDialog
import edu.cqwu.electricity.feedback.util.CrashHandler
import edu.cqwu.electricity.theme.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val FEEDBACK_EMAIL = "2606841932@qq.com"

/**
 * 意见反馈页面
 *
 * 布局：
 * - TopAppBar：标题「意见反馈」+ 返回箭头 + 发送图标按钮
 * - 日志开关（默认关闭）
 * - 「存在历史崩溃记录」提示（有崩溃文件时显示）
 * - 「预览日志」按钮（可预览要附带的日志内容）
 * - 标题输入框（可选）
 * - 内容输入框（必填）
 *
 * 发送方式：通过 Intent.ACTION_SENDTO 唤起系统邮件客户端，
 * Logs come from the in-app buffer plus persisted crash reports (IO dispatcher avoids ANR).
 *
 * 优化：
 * - Preload cache: logs are cached once and reused when previewing or sending
 * - PackageInfo 合并为一次查询，减少重复调用
 * - 日志预览支持长按选择复制
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val topBarColors = currentTopBarColors()
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    // 崩溃记录状态
    var hasCrashReports by remember { mutableStateOf(false) }
    var crashReportCount by remember { mutableIntStateOf(0) }

    // 日志预加载缓存：避免预览和发送时重复读取日志缓冲
    var cachedLogs by remember { mutableStateOf<String?>(null) }
    var isLogsLoading by remember { mutableStateOf(false) }

    // 日志预览对话框
    var showLogPreview by remember { mutableStateOf(false) }
    var previewLogText by remember { mutableStateOf("") }

    val canSend = content.isNotBlank() && !isSending

    // 合并 PackageInfo 查询，只查一次
    val pkgInfo = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }

    // 创建页面时立即预加载日志，确保发送/分享时日志可用
    LaunchedEffect(Unit) {
        isLogsLoading = true
        val (hasReports, count, logs) = withContext(Dispatchers.IO) {
            Triple(
                CrashHandler.hasCrashReports(),
                CrashHandler.crashReportCount(),
                LogCapture.getRecentLogs(context)
            )
        }
        hasCrashReports = hasReports
        crashReportCount = count
        cachedLogs = logs
        isLogsLoading = false
    }

    /** 获取需要附带的日志内容 */
    suspend fun getLogsToAttach(): String {
        if (!includeLogs && !hasCrashReports) return ""
        val raw = cachedLogs ?: withContext(Dispatchers.IO) { LogCapture.getRecentLogs(context) }
        return raw.takeIf { it.isNotBlank() && it != context.getString(R.string.feedback_log_no_logs) } ?: context.getString(R.string.feedback_log_no_logs)
    }

    fun sendByEmail() {
        if (isSending || !canSend) return
        isSending = true

        scope.launch {
            val logs = getLogsToAttach()

            val emailBody = buildString {
                appendLine(context.getString(R.string.feedback_email_content_title))
                if (title.isNotBlank()) appendLine(title)
                appendLine(content)
                appendLine()
                appendLine(context.getString(R.string.feedback_email_device_title))
                appendLine(context.getString(R.string.feedback_email_device, Build.MODEL))
                appendLine(context.getString(R.string.feedback_email_system, Build.VERSION.RELEASE, Build.VERSION.SDK_INT))
                val versionName = pkgInfo?.versionName ?: context.getString(R.string.common_unknown)
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo?.longVersionCode ?: 0L
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo?.versionCode?.toLong() ?: 0L
                }
                appendLine(context.getString(R.string.feedback_email_app_version, versionName, versionCode.toString()))
                appendLine()
                if (logs.isNotBlank()) {
                    appendLine(context.getString(R.string.feedback_email_log_title))
                    append(logs)
                }
            }

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    context.getString(R.string.feedback_email_subject) +
                        if (title.isNotBlank()) context.getString(R.string.feedback_email_title_suffix, title) else ""
                )
                putExtra(Intent.EXTRA_TEXT, emailBody)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                snackbar.show(context.getString(R.string.common_no_mail_app), ToastUtils.Type.ERROR)
            } finally {
                isSending = false
            }
        }
    }

    fun shareLogs() {
        if (isSending) return
        isSending = true

        scope.launch {
            val logs = getLogsToAttach()

            // 日志写入缓存文件作为附件
            val logFile = withContext(Dispatchers.IO) {
                val logDir = File(context.cacheDir, "logs")
                logDir.mkdirs()
                val file = File(logDir, "app_logs.txt")
                file.writeText(logs)
                file
            }

            val logUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                logFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, logUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.common_share_log)))
            } catch (e: Exception) {
                snackbar.show(context.getString(R.string.common_share_failed), ToastUtils.Type.ERROR)
            } finally {
                isSending = false
            }
        }
    }

    fun loadLogPreview() {
        previewLogText = cachedLogs?.ifBlank { context.getString(R.string.feedback_log_empty) }
            ?: context.getString(R.string.feedback_log_loading)
        showLogPreview = true
    }

    // ── 日志预览弹窗（下滑或点击外部关闭） ──
    BottomSheetDialog(
        visible = showLogPreview,
        onDismissRequest = { showLogPreview = false },
        title = stringResource(R.string.feedback_log_preview),
    ) {
        // 外层 BottomSheetDialog(fullscreen=true) 已包含 fillMaxHeight + 滚动，
        // 内容只需 fillMaxWidth，不要固定高度和内层滚动，避免冲突
        SelectionContainer {
            Text(
                text = previewLogText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.feedback_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { sendByEmail() },
                        enabled = canSend && !isSending,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.common_send_email),
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── 日志开关 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.feedback_attach_log),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = includeLogs,
                    onCheckedChange = { includeLogs = it },
                )
            }

            // ── 预览日志按钮 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                TextButton(
                    onClick = { loadLogPreview() },
                    enabled = !isLogsLoading,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(stringResource(R.string.feedback_preview_log))
                }

                TextButton(
                    onClick = { shareLogs() },
                    enabled = !isSending,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(stringResource(R.string.common_share_log))
                }
            }

            // ── 崩溃记录提示 ──
            if (hasCrashReports) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                    text = pluralStringResource(R.plurals.feedback_crash_count, crashReportCount, crashReportCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 标题输入框（可选） ──
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.feedback_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 内容输入框（必填） ──
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.feedback_content_label)) },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 发送中：全屏阻断加载（不可取消）
    if (isSending) {
        LoadingDialog(message = stringResource(R.string.common_loading))
    }
}
