package edu.cqwu.electricity.notice.ui

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.notice.data.NoticeApi
import edu.cqwu.electricity.notice.data.NoticeDetailQp
import edu.cqwu.electricity.notice.data.NoticeItem
import edu.cqwu.electricity.notice.data.formatNoticeTime
import edu.cqwu.electricity.common.settings.isDark
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.webview.util.applyWebViewDarkMode
import edu.cqwu.electricity.webview.util.rememberWebViewDarkModeState
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
    preview: NoticeItem? = null,
    onBack: () -> Unit,
    onOpenInBrowser: (url: String, title: String) -> Unit,
    onReLogin: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<NoticeDetailQp?>(null) }
    val topBarColors = currentTopBarColors()
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requiresReLogin by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val fontScale = LocalAppSettingsState.current.fontScale

    // [preview] 由导航层从 NoticePreviewHolder 取出（用户在列表点击的那一条）：
    // 详情接口返回前先用它渲染标题/发布单位/时间/浏览量。
    // 取不到（进程重建、将来从深链进入）时为 null，页面退化为「整屏加载中」，不会显示旧数据。

    // 文件/下载链接 → 系统浏览器打开（浏览器负责下载）
    fun openExternalBrowser(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            AppLog.d("NoticeDetailScreen", "无浏览器可打开链接，已忽略")
            // 无可用浏览器，忽略
        }
    }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    // 头部（标题 + 元数据行）实测高度；内容区的加载/错误态据此占满「其余整屏」并垂直居中
    var headerHeight by remember { mutableStateOf(0.dp) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

    /**
     * 加载详情。[isRefresh] = true 表示下拉刷新。
     *
     * **不做本地缓存，每次进入都真实请求接口**：浏览量是服务端在详情接口里累加的，
     * 一旦命中本地缓存就跳过请求，浏览量会停在上次的值（服务端统计也随之偏低）。
     *
     * 「内容区是否显示加载中」不用独立状态位判断——detail 为空且没有错误时即为加载中。
     */
    fun loadDetail(isRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (isRefresh) isRefreshing = true
            errorMessage = null
            requiresReLogin = false

            val api = NoticeApi()
            val result = withContext(Dispatchers.IO) { api.fetchNoticeDetail(wid) }
            result.onSuccess { noticeDetail ->
                detail = noticeDetail
                isRefreshing = false
            }.onFailure { e ->
                requiresReLogin = e is SessionExpiredException
                errorMessage = if (requiresReLogin) null else e.message ?: resources.getString(R.string.notice_load_failed)
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
            // 只取消进行中的请求：详情数据不做本地缓存，下次进入会重新拉取最新内容
            loadJob?.cancel()
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
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                // 可视高度已由外层 padding 扣掉 TopAppBar，内容区据此占满整屏
                val availableHeight = maxHeight
                val detailData = detail

                // 头部四个字段优先取详情接口的值，缺失时回退到列表页预加载值。
                // 详情接口返回前先用列表数据渲染头部，返回后逐字段替换为 API 内容。
                val displayTitle = detailData?.noticeTitle?.takeIf { it.isNotBlank() }
                    ?: preview?.noticeTitle.orEmpty()
                val displayDepartment = detailData?.sendDepartment?.takeIf { it.isNotBlank() }
                    ?: preview?.sendDepartment.orEmpty()
                val displayViewCount = detailData?.clickNumber?.takeIf { it.isNotBlank() }
                    ?: preview?.clickNumber.orEmpty()
                val displayTime = detailData?.let { formatNoticeTime(it.sendTime, it.sendTimeDesc) }
                    ?.takeIf { it.isNotBlank() }
                    ?: preview?.let { formatNoticeTime(it.sendTime, it.sendTimeDesc) }.orEmpty()
                val hasHeader = detailData != null || preview != null

                val isDarkMode = LocalAppSettingsState.current.nightMode.isDark(isSystemInDarkTheme())
                val webDarkModeEnabled = rememberWebViewDarkModeState()

                val htmlContent = remember(detailData) {
                    val content = detailData?.noticeContent
                    if (content.isNullOrBlank()) "" else buildHtmlPage(content)
                }

                // 加载/错误内容区占满头部以下的剩余空间，使其在内容区垂直居中；
                // 160dp 下限避免标题很长时把内容压成一条缝
                val contentAreaHeight = (availableHeight - headerHeight).coerceAtLeast(160.dp)

                // 整页统一滚动（PullToRefreshBox 才检测得到下拉）：头部常显，正文/加载/错误同容器切换
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // ── 头部（标题 + 元数据行）：有预加载数据或详情数据即显示，不等详情接口 ──
                    if (hasHeader) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                        ) {
                            // ── 标题 ──
                            Text(
                                text = displayTitle,
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
                                        text = displayDepartment.ifBlank { stringResource(R.string.dashboard_unknown) },
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
                                        text = displayTime,
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
                                        text = displayViewCount,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 已有内容时下拉刷新失败：在内容区上方给一条可重试提示，而不是静默失败
                    if (detailData != null && (errorMessage != null || requiresReLogin)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 8.dp)
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                                .padding(start = 12.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errorMessage ?: stringResource(R.string.login_expired),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    if (requiresReLogin) onReLogin() else loadDetail(isRefresh = true)
                                }
                            ) {
                                Text(stringResource(if (requiresReLogin) R.string.login_relogin else R.string.common_retry))
                            }
                        }
                    }

                    // ── 内容区：正文 / 无正文 / 加载失败 / 加载中，按状态切换 ──
                    when {
                        detailData != null && htmlContent.isNotBlank() -> {
                            // ── WebView（禁用自身滚动，高度动态测量） ──
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (contentHeightPx > 0) {
                                            Modifier.height(with(density) { contentHeightPx.toDp() })
                                        } else {
                                            Modifier.height(contentAreaHeight)
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
                                        settings.allowFileAccess = false
                                        settings.allowContentAccess = false
                                        @Suppress("DEPRECATION")
                                        settings.allowFileAccessFromFileURLs = false
                                        @Suppress("DEPRECATION")
                                        settings.allowUniversalAccessFromFileURLs = false
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            settings.safeBrowsingEnabled = true
                                        }
                                        isVerticalScrollBarEnabled = false
                                        isHorizontalScrollBarEnabled = false
                                        overScrollMode = WebView.OVER_SCROLL_NEVER

                                        webViewClient = object : WebViewClient() {
                                            // 正文里的链接（文件、邮箱、网址等）一律交给系统浏览器或对应 App：
                                            // 本 WebView 是按内容动态测高、禁止自身滚动的特制版，
                                            // 在里面加载一个完整网页会把布局撑坏
                                            // 用被废弃的 String 签名是有意的：它兼容 API 21-23，
                                            // 而 API 24+ 的默认实现会自动转发到这里，一个方法覆盖全版本
                                            @Suppress("OVERRIDE_DEPRECATION")
                                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                                if (url == null) return false
                                                // 锚点与页面内部行为（JS、内嵌数据）放行给 WebView 自己处理
                                                val scheme = Uri.parse(url).scheme?.lowercase()
                                                if (scheme == null || scheme == "about" ||
                                                    scheme == "javascript" || scheme == "data"
                                                ) return false
                                                openExternalBrowser(url)
                                                return true
                                            }

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
                                    // WebView 是原生 View，读不到 Compose 的 Density，字体要自己同步
                                    webView.settings.textZoom = (fontScale * 100).toInt()
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

                        // 详情已返回，但正文为空
                        detailData != null -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(contentAreaHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.notice_no_content),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 加载失败 / 登录过期：只占内容区，头部（预加载或已加载的数据）保持可读
                        requiresReLogin || errorMessage != null -> {
                            ReLoginContent(
                                errorMessage = errorMessage,
                                requiresReLogin = requiresReLogin,
                                onReLogin = onReLogin,
                                onRetry = { loadDetail(isRefresh = true) },
                                modifier = Modifier.height(contentAreaHeight),
                            )
                        }

                        // 其余情况即「首次进入、详情接口未返回」，内容区显示加载中
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(contentAreaHeight),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // 与通知列表页等页面级加载保持一致（默认 40dp / stroke 4dp），
                                    // 不用分页 footer 那种 16dp 小转圈
                                    CircularProgressIndicator()
                                    Text(
                                        text = stringResource(R.string.notice_loading_detail),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            }
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
