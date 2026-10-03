package edu.cqwu.electricity.webview.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebView
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.net.WebVpnEncoder
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.ui.AppScaledDropdownMenu
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.WebViewErrorOverlay
import edu.cqwu.electricity.common.util.ToastUtils
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.webview.util.applyCommonWebViewSettings
import edu.cqwu.electricity.webview.util.applyWebViewDarkMode
import edu.cqwu.electricity.webview.util.rememberWebViewDarkModeState

/**
 * 统一内置浏览器页面
 * 支持两种模式：
 * 1. 通用浏览模式：仅加载 URL 显示网页
 * 2. H5 支付模式：加载 H5 认证地址，由网页自身处理跳转
 * 标题栏同时显示网页标题（加粗）和域名（半透明小字）
 *
 * 初始加载时用与其他页面同款的 PullToRefreshBox + Material3 加载指示器（isRefreshing = isLoading），
 * 网页加载完成后进度条和指示器自动隐藏。
 * 标题栏右上角提供刷新按钮和更多选项菜单（复制链接/分享/在浏览器中打开）。
 *
 * 结构（原单文件按职责拆开，行为不变）：状态在 [UnifiedWebViewUiState]（`UnifiedWebViewState.kt`），
 * 客户端回调与下载/Referer 在 `UnifiedWebViewClients.kt`，外部应用唤起在 `WebViewExternalApp`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedWebViewScreen(
    url: String,
    initialTitle: String = "",
    onClose: () -> Unit,
    reloadAfterLogin: Boolean = false,
    onReloadConsumed: () -> Unit = {}
) {
    val nav = LocalNavController.current
    val webDarkModeEnabled = rememberWebViewDarkModeState()
    val fontScale = LocalAppSettingsState.current.fontScale
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val topBarColors = currentTopBarColors()

    val displayTitle = initialTitle.ifBlank { stringResource(R.string.webview_loading) }
    val state = remember { UnifiedWebViewUiState(displayTitle) }

    // ═══ 文件上传回调 ═══
    // 文件选择器：使用 GetContent 避免 .png 扩展名崩溃，始终用 */* 匹配所有文件类型
    val fileUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        state.fileUploadCallback?.let { callback ->
            callback.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            state.fileUploadCallback = null
        }
    }

    // WebView 回调依赖（context/snackbar 变化时重建；darkMode 与 filePicker 通过闭包捕获）
    val deps = remember(context, snackbar) {
        UnifiedWebViewDeps(
            context = context,
            snackbar = snackbar,
            darkModeEnabled = webDarkModeEnabled,
            launchFilePicker = { fileUploadLauncher.launch("*/*") },
        )
    }

    /**
     * 内/外网通道切换（右上角菜单与错误浮层共用同一份实现）。
     *
     * 统一在当前页面里换 URL，而不是新开一个浏览器页面：内/外网只是同一份内容的两个通道，
     * 做成「新页面」会把返回栈越堆越深（切几次就要按几次返回才能退出）。
     */
    fun toggleNetwork() {
        state.webErrorState = null
        state.webErrorUrl = null
        val currentUrl = state.webViewRef?.url ?: return
        val toggledUrl = try {
            WebVpnEncoder.toggle(currentUrl)
        } catch (e: Exception) {
            AppLog.e("WebView_DIAG", "URL 切换失败: ${e.message}")
            snackbar.show(resources.getString(R.string.webview_url_change_failed), ToastUtils.Type.ERROR)
            null
        } ?: return
        state.webViewRef?.loadUrl(toggledUrl)
    }

    // ═══ 系统返回键：仅在 WebView 有历史记录时拦截 ═══
    // 当 WebView 已到首页时，enabled=false 让系统接管返回手势，
    // 从而触发 Android 14+ 的预测性返回动画（Predictive Back Gesture），
    // 随手势优雅退出当前页面。
    BackHandler(enabled = state.canGoBack) {
        state.webViewRef?.goBack()
    }

    LaunchedEffect(reloadAfterLogin) {
        if (reloadAfterLogin) {
            state.needsReloadAfterReturn = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.pageTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.pageDomain.isNotBlank()) {
                                Text(
                                    text = state.pageDomain,
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
                                val webView = state.webViewRef
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
                        IconButton(onClick = { state.reloadWithLoading() }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.common_refresh),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // ── 更多选项（三点菜单） ──
                        Box {
                            IconButton(onClick = { state.showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.common_more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AppScaledDropdownMenu(
                                expanded = state.showMenu,
                                onDismissRequest = { state.showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.webview_copy_link)) },
                                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                                    onClick = {
                                        state.showMenu = false
                                        val currentUrl = state.webViewRef?.url ?: return@DropdownMenuItem
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText(resources.getString(R.string.webview_clip_label), currentUrl)
                                        clipboard.setPrimaryClip(clip)
                                        snackbar.show(resources.getString(R.string.webview_link_copied), ToastUtils.Type.SUCCESS)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.webview_share_link)) },
                                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                                    onClick = {
                                        state.showMenu = false
                                        val currentUrl = state.webViewRef?.url ?: return@DropdownMenuItem
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, resources.getString(R.string.webview_share_link)))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.webview_open_in_browser)) },
                                    leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                                    onClick = {
                                        state.showMenu = false
                                        val currentUrl = state.webViewRef?.url ?: return@DropdownMenuItem
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                        context.startActivity(intent)
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (WebVpnEncoder.isWebVpnUrl(state.webViewRef?.url ?: ""))
                                                stringResource(R.string.webview_switch_to_external) else stringResource(R.string.webview_switch_to_internal)
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.SwapHoriz, contentDescription = null) },
                                    onClick = {
                                        state.showMenu = false
                                        toggleNetwork()
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

                    // ═══ M3 加载指示器（完整复用其他页面的实现） ═══
                    // PullToRefreshBox 在这里只承担一件事：把 M3 指示器画出来。
                    // 指示器的位置/形变来自 state 内部的 Animatable（distanceFraction），
                    // 而推进它的是 pullToRefresh 节点（bytecode 已核实：仅实现
                    // NestedScrollConnection、没有 pointerInput），裸用 Indicator 时
                    // distanceFraction 恒为 0，指示器就不会出现。
                    // 原生 WebView 不是 NestedScrollingChild、不产生嵌套滚动事件，
                    // 所以这里既不抢 WebView 的触摸，也不存在「下拉触发刷新」的路径；
                    // onRefresh 保持空实现 —— 本页没有刷新功能，它永远不会被调用。
                    PullToRefreshBox(
                        isRefreshing = state.isLoading,
                        onRefresh = {},
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // WebView（立即渲染，无延迟）
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )

                                    applyCommonWebViewSettings(enableZoom = true, showZoomControls = true)
                                    webViewClient = createUnifiedWebViewClient(state, deps)

                                    // ═══ 文件下载支持 ═══
                                    applyUnifiedDownloadListener {
                                        snackbar.show(context.getString(R.string.webview_no_download_tool), ToastUtils.Type.ERROR)
                                    }

                                    webChromeClient = createUnifiedWebChromeClient(state, deps)

                                    state.webViewRef = this

                                    // 根据 URL 域名动态设置 Referer，避免跨域被拒绝
                                    val headers = buildRefererHeaders(url)
                                    // Android 6 系统 WebView 会尝试修改 headers Map，
                                    // 必须使用可变 HashMap 避免 UnsupportedOperationException
                                    loadUrl(url, HashMap(headers))
                                }
                            },
                            update = { webView ->
                                // 同步 canGoBack 状态（兜底，防止回调未及时触发）
                                state.canGoBack = webView.canGoBack()

                                // WebView 是原生 View，读不到 Compose 的 Density，字体要自己同步
                                webView.settings.textZoom = (fontScale * 100).toInt()

                                // 从本地登录返回后自动刷新
                                if (state.needsReloadAfterReturn) {
                                    state.needsReloadAfterReturn = false
                                    onReloadConsumed()
                                    // 同刷新按钮：立刻进入加载态，覆盖服务器响应等待期
                                    state.isLoading = true
                                    webView.reload()
                                }
                                webView.applyWebViewDarkMode(webDarkModeEnabled.value)
                            }
                        )
                    }

                    // 网页加载进度条（放在 AndroidView 之后，确保 Z 轴在 WebView 上方）
                    if (state.progress < 100) {
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    // ═══ 自定义错误叠加层 ═══
                    state.webErrorState?.let { error ->
                        WebViewErrorOverlay(
                            errorCode = error.errorCode,
                            description = error.description,
                            isHttpError = error.isHttpError,
                            onRetry = { state.reloadWithLoading() },
                            onToggleVpn = { toggleNetwork() },
                            onNetworkSettings = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                } catch (_: ActivityNotFoundException) {
                                    snackbar.show(resources.getString(R.string.webview_cannot_open_network_settings), ToastUtils.Type.ERROR)
                                }
                            }
                        )
                    }

                    // ═══ 登录已过期遮罩 ═══
                    if (state.loginRequiredOverlayVisible) {
                        ReLoginContent(
                            requiresReLogin = true,
                            onReLogin = { nav.navigate(Routes.WEBVIEW_LOGIN) },
                            consumeTouches = true,
                        )
                    }
                } // end Box (WebView 内容区)
            } // end Column
        } // end Scaffold
    } // end outer Box
}
