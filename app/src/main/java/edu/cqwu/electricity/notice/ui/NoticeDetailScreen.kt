package edu.cqwu.electricity.notice.ui
import edu.cqwu.electricity.logging.AppLog

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import android.annotation.SuppressLint
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.notice.data.NoticeApi
import edu.cqwu.electricity.notice.data.NoticeDetailQp
import edu.cqwu.electricity.settings.data.isDark
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState
import edu.cqwu.electricity.webview.util.applyWebViewDarkMode
import edu.cqwu.electricity.webview.util.rememberWebViewDarkModeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NoticeDetailScreen(
    wid: String,
    onBack: () -> Unit,
    onOpenInBrowser: (url: String, title: String) -> Unit,
    viewModel: NoticeViewModel? = null,
    onReLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<NoticeDetailQp?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val topBarColors = currentTopBarColors()
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requiresReLogin by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current

    // 文件/下载链接 → 系统浏览器打开（浏览器负责下载）
    fun openExternalBrowser(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            AppLog.d("NoticeDetailScreen", "无浏览器可打开链接，已忽略")
            // 无可用浏览器，忽略
        }
    }
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
            requiresReLogin = false

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
                requiresReLogin = e is SessionExpiredException
                errorMessage = if (requiresReLogin) null else e.message ?: resources.getString(R.string.notice_load_failed)
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
                (errorMessage != null || requiresReLogin) && detail == null -> {
                    ReLoginContent(
                        errorMessage = errorMessage,
                        requiresReLogin = requiresReLogin,
                        onReLogin = onReLogin,
                        onRetry = { loadDetail(isRefresh = true) },
                        modifier = Modifier.heightIn(min = screenHeightDp.dp),
                    )
                }
                detail != null -> {
                    val isDarkMode = LocalAppSettingsState.current.nightMode.isDark(isSystemInDarkTheme())
                    val webDarkModeEnabled = rememberWebViewDarkModeState()
                    val detailData = detail!!

                    if (detailData.noticeContent.isNotBlank()) {
                        val timeDisplay = detailData.sendTimeDesc?.ifBlank { null }
                            ?: detailData.sendTime.take(16)

                        val htmlContent = remember(detailData) {
                            buildHtmlPage(detailData.noticeContent)
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
                                                view.applyWebViewDarkMode(webDarkModeEnabled.value)
                                            }
                                        }
                                        // 下载类响应（服务端 Content-Disposition: attachment 等）→ 外部浏览器
                                        setDownloadListener { downloadUrl, _, _, _, _ ->
                                            openExternalBrowser(downloadUrl)
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
                                    webView.applyWebViewDarkMode(webDarkModeEnabled.value)
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
 * 构建正文 HTML 页面。
 *
 * 夜间模式由 `WebViewDarkMode` 统一处理，但**布局类规则必须留在这里**：
 * 该工具只注入颜色/背景/边框（见其 WEBVIEW_NIGHT_CSS），不含任何换行与宽度约束。
 *
 * `white-space: normal` 是刻意保留的一条：服务端富文本会把正文逐段包进
 * `<span style="text-wrap-mode: nowrap">`（实测 41 个 span 全部带 nowrap），
 * 不压制它则整段文字无法折行、向右溢出屏幕。
 * 它必须带 `!important`——内联样式虽优先级高，但不带 `!important`，
 * 会被带 `!important` 的样式表规则压过。
 * （aef1261 整体移除本样式块导致该问题，d159e0e 补回了部分规则但漏掉这条。）
 *
 * `p { text-indent: 2em }` 针对另一种服务端产物：从 Word 粘贴的落款用**固定像素**
 * 缩进做右对齐（实测「基建后勤处」那行是 `text-indent:419px`），
 * 该值超过手机屏幕逻辑宽度，整行被推出屏幕外、只露出第一个字的一角。
 * 统一成 2em 后：正常段落原本就是 `28px`（字号 14px × 2），视觉不变；
 * 异常段落被拉回可见范围。本规则必须排在 `body *` 之后——两者优先级相同，
 * 同为 `!important` 时后出现者生效。
 *
 * `img` 与 `table` 两条同样防溢出：图片限宽并在超宽时等比缩放（缺 `height: auto`
 * 会把图片拉变形，`display: block` + `margin: auto` 让它居中）；
 * 表格强制占满容器宽度——服务端从 Word 粘贴的表格常带固定像素宽度，会撑破屏幕。
 * 这两条同为普通选择器优先级，也排在 `body *` 之后。
 */
private fun buildHtmlPage(noticeContent: String): String {
    return """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
body * {
    max-width: 100% !important;
    word-break: break-word !important;
    overflow-wrap: break-word !important;
    white-space: normal !important;
}
p {
    text-indent: 2em !important;
}
img {
    max-width: 100% !important;
    height: auto !important;
    display: block;
    margin: 8px auto;
}
table {
    width: 100% !important;
    border-collapse: collapse;
    margin: 8px 0;
}
</style>
</head>
<body>
$noticeContent
</body>
</html>""".trimIndent()
}
