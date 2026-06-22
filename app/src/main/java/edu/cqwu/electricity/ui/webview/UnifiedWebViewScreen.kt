package edu.cqwu.electricity.ui.webview

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import edu.cqwu.electricity.data.network.common.WebVpnEncoder
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.WebViewErrorOverlay
import edu.cqwu.electricity.ui.navigation.Routes
import edu.cqwu.electricity.ui.theme.LocalNavController
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils
import edu.cqwu.electricity.util.WebViewUrlUtil
import java.io.ByteArrayInputStream

/**
 * 统一内置浏览器页面
 * 支持两种模式：
 * 1. 通用浏览模式：仅加载 URL 显示网页
 * 2. H5 支付模式：加载 H5 认证地址，由网页自身处理跳转
 * 标题栏同时显示网页标题（加粗）和域名（半透明小字）
 *
 * 初始加载时 SwipeRefreshLayout 显示顶部旋转刷新指示器（不响应下拉手势），
 * 网页加载完成后进度条和指示器自动隐藏。
 * 标题栏右上角提供刷新按钮和更多选项菜单（复制链接/分享/在浏览器中打开）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UnifiedWebViewScreen(
    url: String,
    initialTitle: String = "",
    onClose: () -> Unit,
    skipNextCasRedirect: Boolean = false,
    onSkipConsumed: () -> Unit = {}
) {
    val nav = LocalNavController.current
    // Campusphere 提醒：只弹一次
    var campusphereToastShown by remember { mutableStateOf(false) }

    // 加载状态
    var isLoading by remember { mutableStateOf(true) }

    // WebView 加载错误状态：null 表示无错误，非 null 表示显示自定义错误叠加层
    data class WebViewError(
        val errorCode: Int,
        val description: String,
        val isHttpError: Boolean = false
    )
    var webErrorState by remember { mutableStateOf<WebViewError?>(null) }

    // 跟踪 WebView 历史栈状态，动态控制 BackHandler
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(10) }
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val displayTitle = initialTitle.ifBlank { stringResource(R.string.webview_loading) }
    var pageTitle by remember { mutableStateOf(displayTitle) }
    val snackbar = LocalSnackbarController.current
    var pageDomain by remember { mutableStateOf("") }
    // 控制三点溢出菜单
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // ═══ 文件上传回调 ═══
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    // 文件选择器：使用 GetContent 避免 .png 扩展名崩溃，始终用 */* 匹配所有文件类型
    val fileUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        fileUploadCallback?.let { callback ->
            callback.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            fileUploadCallback = null
        }
    }

    // SwipeRefreshLayout 顶部旋转指示器状态（仅初始加载时显示，不响应下拉手势）
    var isWebViewRefreshing by remember { mutableStateOf(true) }
    // 主题色（用于 SwipeRefreshLayout 指示器颜色）
    val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    val tertiaryColorArgb = MaterialTheme.colorScheme.tertiary.toArgb()

    // WebView 引用，用于 WebView 操作（返回、刷新、复制链接等）
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // 标记：从本地登录返回后需要自动刷新 WebView，加载网页版登录
    var needsReloadAfterReturn by remember { mutableStateOf(false) }

    // ═══ CAS 登录检测状态 ═══
    var pendingLoginNavigation by remember { mutableStateOf(false) }
    var lastCheckedCasUrl by remember { mutableStateOf<String?>(null) }

    // ═══ 系统返回键：仅在 WebView 有历史记录时拦截 ═══
    // 当 WebView 已到首页时，enabled=false 让系统接管返回手势，
    // 从而触发 Android 14+ 的预测性返回动画（Predictive Back Gesture），
    // 随手势优雅退出当前页面。
    BackHandler(enabled = canGoBack) {
        webViewRef.value?.goBack()
    }

    // 同步 isLoading → isWebViewRefreshing，控制初始加载时顶部旋转指示器的显示/隐藏
    LaunchedEffect(isLoading) {
        isWebViewRefreshing = isLoading
    }

    // ═══ CAS 登录检测 ═══
    LaunchedEffect(pendingLoginNavigation) {
        if (pendingLoginNavigation) {
            Log.d("WebView_DIAG", ">>> CAS登录检测触发，准备跳转到本地登录")
            pendingLoginNavigation = false
            nav.navigate(Routes.LOGIN)
        }
    }

    LaunchedEffect(skipNextCasRedirect) {
        if (skipNextCasRedirect) {
            needsReloadAfterReturn = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pageTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (pageDomain.isNotBlank()) {
                            Text(
                                text = pageDomain,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    Row {
                        // ← 返回上一页（WebView 历史栈）；无法返回时降级为关闭浏览器，与系统返回键行为一致
                        IconButton(onClick = {
                            val webView = webViewRef.value
                            if (webView != null && webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                onClose()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // ✕ 关闭内置浏览器
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // ── 刷新按钮 ──
                    IconButton(onClick = {
                        webViewRef.value?.reload()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.common_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ── 更多选项（三点菜单） ──
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.common_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.webview_copy_link)) },
                                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val url = webViewRef.value?.url ?: return@DropdownMenuItem
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText(context.getString(R.string.webview_clip_label), url)
                                    clipboard.setPrimaryClip(clip)
                                    snackbar.show(context.getString(R.string.webview_link_copied), ToastUtils.Type.SUCCESS)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.webview_share_link)) },
                                leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val url = webViewRef.value?.url ?: return@DropdownMenuItem
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, url)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.webview_share_link)))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.webview_open_in_browser)) },
                                leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val url = webViewRef.value?.url ?: return@DropdownMenuItem
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (WebVpnEncoder.isWebVpnUrl(webViewRef.value?.url ?: ""))
                                            stringResource(R.string.webview_switch_to_external) else stringResource(R.string.webview_switch_to_internal)
                                    )
                                },
                                leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val currentUrl = webViewRef.value?.url ?: return@DropdownMenuItem
                                    val toggledUrl = try {
                                        if (WebVpnEncoder.isWebVpnUrl(currentUrl)) {
                                            WebVpnEncoder.decode(currentUrl)
                                        } else {
                                            WebVpnEncoder.transform(currentUrl)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("WebView_DIAG", "URL 切换失败: ${e.message}")
                                        snackbar.show(context.getString(R.string.webview_url_change_failed), ToastUtils.Type.ERROR)
                                        null
                                    }
                                    if (toggledUrl != null) {
                                        nav.navigate(Routes.unifiedWebViewRoute(toggledUrl, pageTitle))
                                    }
                                }
                            )
                        }
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // WebView 内容区（含进度条和错误叠加层）
        Box(modifier = Modifier.fillMaxSize()) {

            // WebView（立即渲染，无延迟）
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    // SwipeRefreshLayout 仅作为"壳"保留加载旋转动画
                    // isEnabled = false 完全禁用下拉手势，所有触摸事件直接透传给 WebView
                    SwipeRefreshLayout(context).apply {
                        isEnabled = false
                        setColorSchemeColors(primaryColorArgb, tertiaryColorArgb)

                        setOnRefreshListener {
                            // 永远不会被触发（isEnabled = false）
                        }

                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            settings.javaScriptEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.setSupportZoom(true)
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = true
                            settings.userAgentString = edu.cqwu.electricity.data.network.common.UserAgentProvider.getActiveUserAgent()

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    Log.d("WebView_DIAG", "onPageStarted: $url")
                                    isLoading = true
                                    webErrorState = null  // 新页面开始加载时清除错误状态
                                    canGoBack = view?.canGoBack() == true

                                    // ═══ campusphere.net 域名检测（仅一次） ═══
                                    if (url != null && !campusphereToastShown) {
                                        val host = Uri.parse(url).host
                                        if (host != null && host.endsWith(".campusphere.net")) {
                                            campusphereToastShown = true
                                            val currentUrl = url
                                            snackbar.show(
                                                message = context.getString(R.string.webview_campusphere_warning),
                                                actionLabel = context.getString(R.string.common_open_in_browser),
                                                onAction = {
                                                    try {
                                                        // 优先尝试打开今日校园 App
                                                        val campusIntent = Intent(Intent.ACTION_VIEW, Uri.parse("campusnextins://"))
                                                        context.startActivity(campusIntent)
                                                    } catch (_: ActivityNotFoundException) {
                                                        // 降级：用浏览器打开当前链接
                                                        try {
                                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                                            context.startActivity(browserIntent)
                                                        } catch (_: ActivityNotFoundException) {
                                                            snackbar.show(context.getString(R.string.common_no_browser))
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    if (url != null && WebViewUrlUtil.isCasLoginUrl(url)) {
                                        if (skipNextCasRedirect) {
                                            onSkipConsumed()
                                            lastCheckedCasUrl = url
                                            return
                                        }
                                        if (url != lastCheckedCasUrl) {
                                            lastCheckedCasUrl = url
                                            Log.d("WebView_DIAG", ">>> onPageStarted 检测到CAS登录页，用户未登录")
                                            view?.stopLoading()
                                            pendingLoginNavigation = true
                                        }
                                        return
                                    } else if (url != null) {
                                        lastCheckedCasUrl = null
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    Log.d("WebView_DIAG", "onPageFinished: $url")
                                    isLoading = false
                                    canGoBack = view?.canGoBack() == true

                                    // 注入 JS 强制启用缩放：
                                    // 1. 移除页面的 user-scalable=no 限制
                                    // 2. 覆盖 CSS touch-action 阻止浏览器缩放引擎的限制
                                    view?.evaluateJavascript(
                                        """(function() {
                                            var meta = document.querySelector('meta[name="viewport"]');
                                            if (meta) {
                                                var content = meta.getAttribute('content') || '';
                                                if (content.indexOf('user-scalable=no') !== -1) {
                                                    meta.setAttribute('content', content.replace(/user-scalable=no/gi, 'user-scalable=yes'));
                                                }
                                            }
                                            var style = document.createElement('style');
                                            style.setAttribute('type', 'text/css');
                                            style.appendChild(document.createTextNode(
                                                'html, body, * { touch-action: manipulation !important; }'
                                            ));
                                            document.head.appendChild(style);
                                        })()""", null)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true && webErrorState == null) {
                                        val code = error?.errorCode ?: -1
                                        val desc = error?.description?.toString() ?: context.getString(R.string.common_unknown_error)
                                        Log.w("WebView_DIAG", ">>> 主框架加载错误: code=$code, desc=$desc")
                                        isLoading = false
                                        webErrorState = WebViewError(
                                            errorCode = code,
                                            description = desc
                                        )
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    val statusCode = errorResponse?.statusCode ?: 0
                                    if (request?.isForMainFrame == true && statusCode >= 400 && webErrorState == null) {
                                        Log.w("WebView_DIAG", ">>> HTTP 错误: statusCode=$statusCode")
                                        isLoading = false
                                        webErrorState = WebViewError(
                                            errorCode = statusCode,
                                            description = "HTTP $statusCode",
                                            isHttpError = true
                                        )
                                    }
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val url = request?.url?.toString() ?: return null
                                    if (url.contains("campushoy")) {
                                        Log.d("WebView_DIAG", ">>> 拦截 campushoy.js: $url")
                                        return WebResourceResponse(
                                            "application/javascript", "UTF-8",
                                            ByteArrayInputStream("".toByteArray())
                                        )
                                    }
                                    return null
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    Log.d("WebView_DIAG", "shouldOverrideUrlLoading: $url")

                                    return view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, url, "WebView_DIAG")
                                }

                                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                    super.doUpdateVisitedHistory(view, url, isReload)
                                    canGoBack = view?.canGoBack() == true
                                }

                                @Deprecated("Deprecated in Java")
                                @Suppress("DEPRECATION")
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        return false
                                    }
                                    if (url != null) {
                                        if (view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, url, "WebView_DIAG")) {
                                            return true
                                        }
                                    }
                                    return false
                                }
                            }

                            // ═══ 文件下载支持 ═══
                            setDownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                                Log.d("WebView_DIAG", "下载请求: $downloadUrl, mime=$mimeType")
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                    context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    snackbar.show(context.getString(R.string.webview_no_download_tool), ToastUtils.Type.ERROR)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                    if (newProgress == 100) {
                                        isLoading = false
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    Log.d("WebView_DIAG", "onReceivedTitle: $title, url=${view?.url}")
                                    if (!title.isNullOrBlank()) {
                                        pageTitle = title
                                    }
                                    view?.url?.let { url ->
                                        try {
                                            val host = Uri.parse(url).host
                                            if (!host.isNullOrBlank()) {
                                                pageDomain = host
                                            }
                                        } catch (_: Exception) { }
                                    }
                                }

                                // ═══ 文件上传支持（<input type="file">）═══
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    Log.d("WebView_DIAG", "onShowFileChooser")
                                    fileUploadCallback = filePathCallback
                                    // 始终用 */* 避免 WebView 传 .png 等扩展名导致崩溃
                                    fileUploadLauncher.launch("*/*")
                                    return true
                                }
                            }

                            webViewRef.value = this

                            // 根据 URL 域名动态设置 Referer，避免跨域被拒绝
                            val headers = buildRefererHeaders(url)
                            // Android 6 系统 WebView 会尝试修改 headers Map，
                            // 必须使用可变 HashMap 避免 UnsupportedOperationException
                            loadUrl(url, HashMap(headers))
                        }.also { webView ->
                            addView(webView)
                        }
                    }
                },
                update = { swipeRefreshLayout ->
                    // 同步 isRefreshing 状态：初始加载时显示旋转指示器，加载完成自动隐藏
                    swipeRefreshLayout.isRefreshing = isWebViewRefreshing

                    // 同步 canGoBack 状态（兜底，防止回调未及时触发）
                    val webView = swipeRefreshLayout.getChildAt(0) as? WebView
                    canGoBack = webView?.canGoBack() == true

                    // 从本地登录返回后自动刷新
                    if (needsReloadAfterReturn) {
                        needsReloadAfterReturn = false
                        webView?.reload()
                    }
                }
            )

            // 网页加载进度条（放在 AndroidView 之后，确保 Z 轴在 WebView 上方）
            if (progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // ═══ 自定义错误叠加层 ═══
            webErrorState?.let { error ->
                WebViewErrorOverlay(
                    errorCode = error.errorCode,
                    description = error.description,
                    isHttpError = error.isHttpError,
                    onRetry = {
                        webErrorState = null
                        webViewRef.value?.reload()
                    },
                    onToggleVpn = {
                        webErrorState = null
                        val currentUrl = webViewRef.value?.url
                        if (currentUrl != null) {
                            val toggledUrl = try {
                                if (WebVpnEncoder.isWebVpnUrl(currentUrl)) {
                                    WebVpnEncoder.decode(currentUrl)
                                } else {
                                    WebVpnEncoder.transform(currentUrl)
                                }
                            } catch (e: Exception) {
                                Log.e("WebView_DIAG", "URL切换失败: ${e.message}")
                                null
                            }
                            if (toggledUrl != null) {
                                webViewRef.value?.loadUrl(toggledUrl)
                            }
                        }
                    },
                    onNetworkSettings = {
                        try {
                            context.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
                        } catch (_: ActivityNotFoundException) {
                            snackbar.show(context.getString(R.string.webview_cannot_open_network_settings), ToastUtils.Type.ERROR)
                        }
                    }
                )
            }
        } // end Box (WebView 内容区)
    } // end Column
        } // end Scaffold
    } // end outer Box
}

/**
 * 根据 URL 域名动态构建 Referer 请求头。
 * 仅对已知需要 Referer 的域名设置，避免跨域访问被拒绝。
 */
private fun buildRefererHeaders(url: String): Map<String, String> {
    return when {
        url.contains("pay.cqwu.edu.cn") -> mapOf("Referer" to "https://pay.cqwu.edu.cn/")
        else -> emptyMap()
    }
}
