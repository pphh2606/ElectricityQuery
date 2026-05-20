package edu.cqwu.electricity.ui.notice

import android.content.Intent
import android.graphics.Color
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import edu.cqwu.electricity.data.local.NightMode
import edu.cqwu.electricity.ui.theme.LocalNightModeState
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCoroutineScope
import edu.cqwu.electricity.data.model.NoticeDetailQp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeDetailScreen(
    wid: String,
    onBack: () -> Unit,
    onOpenInBrowser: (url: String, title: String) -> Unit,
    viewModel: NoticeViewModel? = null
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<NoticeDetailQp?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val screenHeightPx = remember {
        context.resources.displayMetrics.heightPixels
    }
    val screenHeightDp = with(context.resources.displayMetrics) {
        (heightPixels / density).toInt()
    }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    fun loadDetail(isRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (isRefresh) {
                isRefreshing = true
            } else {
                isLoading = true
            }
            errorMessage = null

            if (!isRefresh && viewModel != null) {
                val cached = withContext(Dispatchers.IO) { viewModel.getDetail(wid) }
                if (cached != null) {
                    detail = cached
                    isLoading = false
                    isRefreshing = false
                    return@launch
                }
            }

            val api = NoticeApi()
            val result = withContext(Dispatchers.IO) { api.fetchNoticeDetail(wid) }
            result.onSuccess { noticeDetail ->
                detail = noticeDetail
                viewModel?.let { vm ->
                    vm.putDetail(wid, noticeDetail)
                }
                isLoading = false
                isRefreshing = false
            }.onFailure { e ->
                errorMessage = e.message ?: "加载详情失败"
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(wid) {
        loadDetail()
    }

    fun shareNotice() {
        val detailData = detail ?: return
        val shareText = buildString {
            appendLine(detailData.noticeTitle)
            appendLine()
            val plainText = Html.fromHtml(detailData.noticeContent, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace(Regex("[ \t]+"), " ")
                .replace(Regex("\n\\s*\n"), "\n\n")
                .trim()
            append(plainText)
        }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "分享通知")
        context.startActivity(shareIntent)
    }

    DisposableEffect(wid) {
        onDispose {
            loadJob?.cancel()
            detail = null
            isLoading = true
            errorMessage = null
            viewModel?.removeDetail(wid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "通知详情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (detail != null) {
                        IconButton(onClick = { shareNotice() }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "分享",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                val detailData = detail ?: return@IconButton
                                val url = "https://ehall.cqwu.edu.cn/publicapp/sys/tzggxt/mobile/index.html#!/detail?noticeId=$wid"
                                onOpenInBrowser(url, detailData.noticeTitle)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "在浏览器中打开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadDetail(isRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && detail == null -> {
                    val screenH = screenHeightDp.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = screenH),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "正在加载详情...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                errorMessage != null && detail == null -> {
                    val screenH = screenHeightDp.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .heightIn(min = screenH),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp)
                        )
                        Text(
                            text = "下拉刷新重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
                detail != null -> {
                    val isDarkMode = when (LocalNightModeState.current.nightMode) {
                        NightMode.SYSTEM -> isSystemInDarkTheme()
                        NightMode.LIGHT -> false
                        NightMode.DARK -> true
                    }
                    val detailData = detail!!

                    if (detailData.noticeContent.isNotBlank()) {
                        val timeDisplay = detailData.sendTimeDesc?.ifBlank { null }
                            ?: detailData.sendTime.take(16)

                        val htmlContent = remember(detailData, isDarkMode) {
                            buildHtmlPage(
                                noticeContent = detailData.noticeContent,
                                isDarkMode = isDarkMode
                            )
                        }

                        // Column + verticalScroll 统一滚动，PullToRefreshBox 可检测下拉
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            // ── 标题 ──
                            Text(
                                text = detailData.noticeTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 20.dp)
                            )

                            // ── 元数据行 ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .background(
                                        if (isDarkMode) ComposeColor.White.copy(alpha = 0.06f)
                                        else ComposeColor.Black.copy(alpha = 0.04f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "发布单位",
                                        modifier = Modifier.padding(end = 3.dp).size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detailData.sendDepartment.ifBlank { "未知" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "发布时间",
                                        modifier = Modifier.padding(end = 3.dp).size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = timeDisplay,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = "浏览次数",
                                        modifier = Modifier.padding(end = 3.dp).size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detailData.clickNumber,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // ── WebView（禁用自身滚动，高度动态测量） ──
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (contentHeightPx > 0) {
                                            Modifier.height(with(androidx.compose.ui.platform.LocalDensity.current) { contentHeightPx.toDp() })
                                        } else {
                                            Modifier.height(with(androidx.compose.ui.platform.LocalDensity.current) { screenHeightPx.toDp() })
                                        }
                                    ),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                        setBackgroundColor(Color.TRANSPARENT)
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = false
                                        isVerticalScrollBarEnabled = false
                                        isHorizontalScrollBarEnabled = false
                                        overScrollMode = WebView.OVER_SCROLL_NEVER

                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView, url: String?) {
                                                super.onPageFinished(view, url)
                                                // 延迟一帧后使用原生 measure() 获取完整内容高度
                                                // 避免 JS scrollHeight 在受限布局下返回不正确值
                                                view.postDelayed({
                                                    // 方案一：原生 measure() — UNSPECIFIED 测量完整内容
                                                    val widthSpec = View.MeasureSpec.makeMeasureSpec(
                                                        view.width.coerceAtLeast(1), View.MeasureSpec.EXACTLY
                                                    )
                                                    val heightSpec = View.MeasureSpec.makeMeasureSpec(
                                                        0, View.MeasureSpec.UNSPECIFIED
                                                    )
                                                    view.measure(widthSpec, heightSpec)
                                                    val measuredHeight = view.measuredHeight

                                                    // 方案二：contentHeight 作为备选
                                                    val cssHeight = view.contentHeight
                                                    val density = view.resources.displayMetrics.density
                                                    val pxFromContent = (cssHeight * density).toInt()

                                                    val finalHeight = maxOf(measuredHeight, pxFromContent)
                                                    if (finalHeight > 0) {
                                                        contentHeightPx = finalHeight
                                                    }
                                                }, 100)
                                            }
                                        }
                                        tag = htmlContent.hashCode()
                                        loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                                    }
                                },
                                update = { webView ->
                                    val currentHash = htmlContent.hashCode()
                                    if (webView.tag != currentHash) {
                                        webView.tag = currentHash
                                        contentHeightPx = 0 // 重置，重新测量
                                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                                    }
                                },
                                onRelease = { webView ->
                                    webView.stopLoading()
                                    webView.loadUrl("about:blank")
                                    webView.clearHistory()
                                    webView.removeAllViews()
                                    webView.destroy()
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无内容", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 构建纯内容 HTML（无标题/元数据，仅正文样式）
 */
private fun buildHtmlPage(
    noticeContent: String,
    isDarkMode: Boolean
): String {
    val textColor = if (isDarkMode) "#e0e0e0" else "#333333"
    val linkColor = if (isDarkMode) "#4dabf7" else "#1976D2"
    val tableBorder = if (isDarkMode) "#555555" else "#dddddd"
    val codeBg = if (isDarkMode) "#2d2d2d" else "#f5f5f5"

    return """<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
body {
    font-size: 16px; line-height: 1.7; color: $textColor;
    padding: 0 16px 16px; margin: 0;
    word-wrap: break-word; overflow-wrap: break-word;
    overflow-x: hidden;
}
/* 透明背景：整体和常见容器 */
body, div, p, span, table, td, th, li, h1, h2, h3, h4, h5, h6 {
    background-color: transparent !important;
}
/* 强制覆盖服务端内联样式，统一排版 */
body * {
    font-size: 16px !important;
    color: $textColor !important;
    line-height: 1.7 !important;
    text-indent: 0 !important;
    white-space: normal !important;
    word-break: break-word !important;
    overflow-wrap: break-word !important;
    max-width: 100% !important;
    box-sizing: border-box !important;
}
/* 排除 pre/code 背景和字体，保留其样式 */
pre, code {
    background: $codeBg !important;
    padding: 2px 4px; border-radius: 4px;
    font-family: monospace;
}
pre *, code * {
    font-family: monospace !important;
}
img { max-width: 100% !important; height: auto !important; display: block; margin: 8px auto; }
table { width: 100% !important; border-collapse: collapse; margin: 8px 0; }
table, th, td { border: 1px solid $tableBorder; padding: 6px 8px; }
p { margin: 8px 0; }
a { color: $linkColor; word-break: break-all; }
video { max-width: 100% !important; height: auto; }
</style>
</head>
<body>
$noticeContent
</body>
</html>""".trimIndent()
}
