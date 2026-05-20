package edu.cqwu.electricity.ui.feedback

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.CrashHandler
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    // 启动时检查是否有崩溃记录
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            hasCrashReports = CrashHandler.hasCrashReports()
            crashReportCount = CrashHandler.crashReportCount()
        }
    }

    // 日志开关打开或关闭时，重新加载/清空缓存
    LaunchedEffect(includeLogs) {
        if (includeLogs || hasCrashReports) {
            isLogsLoading = true
            cachedLogs = withContext(Dispatchers.IO) { LogCapture.getRecentLogs() }
            isLogsLoading = false
        } else {
            cachedLogs = null
        }
    }

    fun sendFeedback() {
        if (isSending || !canSend) return
        isSending = true

        scope.launch {
            // 使用缓存日志，不再重复执行 logcat
            // 如果日志还在加载中则等待一下（极低概率，因为预加载在 LaunchedEffect 中已完成）
            val logs = if (includeLogs || hasCrashReports) {
                if (cachedLogs != null) cachedLogs!!
                else withContext(Dispatchers.IO) { LogCapture.getRecentLogs() }
            } else ""

            val deviceInfo = buildString {
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
                data = android.net.Uri.parse("mailto:2606841932@qq.com")
                putExtra(Intent.EXTRA_SUBJECT, "电费查询 App 反馈${if (title.isNotBlank()) "：$title" else ""}")
                putExtra(Intent.EXTRA_TEXT, deviceInfo)
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

    // ── 日志预览对话框 ──
    if (showLogPreview) {
        AlertDialog(
            onDismissRequest = { showLogPreview = false },
            title = {
                Text("日志预览", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 支持长按选择复制日志内容
                    SelectionContainer {
                        Text(
                            text = previewLogText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogPreview = false }) {
                    Text("关闭")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "意见反馈",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
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
                            onClick = { sendFeedback() },
                            enabled = canSend,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送反馈",
                            )
                        }
                    }
                },
                colors = topBarColors,
            )
        },
        modifier = Modifier.navigationBarsPadding(),
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
                    text = "附带应用日志（含崩溃记录）",
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
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    Text("预览日志")
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
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = "存在 $crashReportCount 条历史崩溃记录，发送时将自动附带",
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
                label = { Text("标题（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── 内容输入框（必填） ──
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("反馈内容") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

}
