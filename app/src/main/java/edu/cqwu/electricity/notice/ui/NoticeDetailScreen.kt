package edu.cqwu.electricity.notice.ui

import android.annotation.SuppressLint
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import edu.cqwu.electricity.settings.data.NightMode
import edu.cqwu.electricity.notice.data.NoticeApi
import edu.cqwu.electricity.notice.data.NoticeDetailQp
import edu.cqwu.electricity.theme.ui.LocalNightModeState
import edu.cqwu.electricity.theme.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.toTopAppBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color as ComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
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
    val resources = LocalResources.current
    val screenHeightPx = remember {
        resources.displayMetrics.heightPixels
    }
    val screenHeightDp = with(resources.displayMetrics) {
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
                viewModel?.putDetail(wid, noticeDetail)
                isLoading = false
                isRefreshing = false
            }.onFailure { e ->
                errorMessage = e.message ?: resources.getString(R.string.notice_load_failed)
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
            val plainText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(detailData.noticeContent, Html.FROM_HTML_MODE_LEGACY)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(detailData.noticeContent)
                }
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
        val shareIntent = Intent.createChooser(sendIntent, resources.getString(R.string.notice_share))
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
                        text = stringResource(R.string.notice_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    if (detail != null) {
                        IconButton(onClick = { shareNotice() }) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.common_share),
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
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = stringResource(R.string.common_open_in_browser),
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
                            text = stringResource(R.string.notice_loading_detail),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                errorMessage != null && detail == null -> {
                    ReLoginContent(
                        errorMessage = errorMessage,
                        requiresReLogin = false,
                        onReLogin = {},
                        onRetry = { loadDetail(isRefresh = true) },
                        modifier = Modifier.heightIn(min = screenHeightDp.dp),
                    )
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
                                        imageVector = Icons.Outlined.Person,
                                        contentDescription = stringResource(R.string.notice_publisher),
                                        modifier = Modifier.padding(end = 3.dp).size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = detailData.sendDepartment.ifBlank { stringResource(R.string.dashboard_unknown) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccessTime,
                                        contentDescription = stringResource(R.string.notice_publish_time),
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
                                        imageVector = Icons.Outlined.Visibility,
                                        contentDescription = stringResource(R.string.notice_view_count),
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
                                        settings.javaScriptCanOpenWindowsAutomatically = false
                                        settings.mediaPlaybackRequiresUserGesture = true
                                        settings.setAllowFileAccess(false)
                                        settings.setAllowContentAccess(false)
                                        @Suppress("DEPRECATION")
                                        settings.setAllowFileAccessFromFileURLs(false)
                                        @Suppress("DEPRECATION")
                                        settings.setAllowUniversalAccessFromFileURLs(false)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            settings.safeBrowsingEnabled = true
                                        }
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
                            Text(stringResource(R.string.notice_no_content), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
