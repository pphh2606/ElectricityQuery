package edu.cqwu.electricity.feedback.ui

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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.toTopAppBarColors
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
 * 附带的日志通过持久化崩溃文件 + logcat 命令收集（零依赖，在 IO 协程中执行避免 ANR）。
 *
 * 优化：
 * - 预加载缓存：日志开关打开时自动预加载并缓存日志，避免预览和发送时重复执行 logcat
 * - PackageInfo 合并为一次查询，减少重复调用
 * - 日志预览支持长按选择复制
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var includeLogs by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    // 崩溃记录状态
    var hasCrashReports by remember { mutableStateOf(false) }
    var crashReportCount by remember { mutableStateOf(0) }

    // 日志预加载缓存：避免预览和发送时重复执行 logcat
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
                LogCapture.getRecentLogs()
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
        val raw = cachedLogs ?: withContext(Dispatchers.IO) { LogCapture.getRecentLogs() }
        return raw.takeIf { it.isNotBlank() && it != "(未获取到日志)" } ?: "(未获取到日志)"
    }

    fun sendByEmail() {
        if (isSending || !canSend) return
        isSending = true

        scope.launch {
            val logs = getLogsToAttach()

            val emailBody = buildString {
                appendLine("--- 反馈内容 ---")
                if (title.isNotBlank()) appendLine(title)
                appendLine(content)
                appendLine()
                appendLine("--- 设备信息 ---")
                appendLine("设备: ${Build.MODEL}")
                appendLine("系统: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                val versionName = pkgInfo?.versionName ?: "未知"
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo?.longVersionCode ?: 0L
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo?.versionCode?.toLong() ?: 0L
                }
                appendLine("App 版本: $versionName ($versionCode)")
                appendLine()
                if (logs.isNotBlank()) {
                    appendLine("--- 日志 ---")
                    append(logs)
                }
            }

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, "电费查询 App 反馈${if (title.isNotBlank()) "：$title" else ""}")
                putExtra(Intent.EXTRA_TEXT, emailBody)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                snackbar.show("未找到邮件应用，请安装邮箱客户端", ToastUtils.Type.ERROR)
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
                context.startActivity(Intent.createChooser(intent, "分享日志"))
            } catch (e: Exception) {
                snackbar.show("分享失败", ToastUtils.Type.ERROR)
            } finally {
                isSending = false
            }
        }
    }

    fun loadLogPreview() {
        if (cachedLogs != null) {
            previewLogText = cachedLogs!!.ifBlank { "(无日志内容)" }
            showLogPreview = true
        } else {
            // 理论上缓存一定会存在（LaunchedEffect 中已加载），但兜底直接拉取
            previewLogText = "(日志加载中，请稍后再试)"
            showLogPreview = true
        }
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
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = { sendByEmail() },
                            enabled = canSend,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send,
                                contentDescription = stringResource(R.string.common_send_email),
                            )
                        }
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
                    if (isLogsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(16.dp)
                                .width(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Visibility,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
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
                        text = stringResource(R.string.feedback_crash_count, crashReportCount),
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

}
