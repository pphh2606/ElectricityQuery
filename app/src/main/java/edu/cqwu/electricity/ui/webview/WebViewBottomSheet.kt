package edu.cqwu.electricity.ui.webview

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.network.common.UserAgentProvider
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.WebViewErrorOverlay
import edu.cqwu.electricity.util.ToastUtils
import edu.cqwu.electricity.util.WebViewUrlUtil
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "WebViewBottomSheet"

/**
 * 半屏 WebView 弹窗（自定义实现）
 *
 * 完全自定义的 Bottom Sheet，不依赖 ModalBottomSheet，三条约束同时满足：
 * 1. 拖拽手柄改变弹窗高度（pointerInput 实时更新 sheetHeight）
 * 2. WebView 内上下滚动（原生 View 触摸，不经过 Compose 手势系统）
 * 3. 拖拽跟手（onDrag 中实时更新高度，无 AnchoredDraggable 吸附）
 *
 * 手柄区域用 Compose pointerInput，WebView 用原生 View.onTouchEvent()，
 * 两者在 Android 事件分发层面完全分离，物理空间不重叠，手势零冲突。
 *
 * @param visible 控制弹窗显隐
 * @param onDismissRequest 弹窗关闭回调
 * @param url 要加载的 URL
 * @param title 初始标题（加载中显示）
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    url: String,
    title: String = "",
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ── 尺寸计算（统一 Dp）──
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val minHeight = screenHeight * 0.5f
    val maxHeight = screenHeight
    val handleBarHeight = 60.dp

    // ── 高度状态（初始为 0，由打开动画从 0 到 minHeight）──
    val heightAnimatable = remember { Animatable(0f) }
    var sheetHeight by remember { mutableStateOf(0.dp) }

    // ── isHiding 模式（退出动画）──
    var isHiding by remember { mutableStateOf(false) }

    // ── 其他状态 ──
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(10) }
    var pageTitle by remember { mutableStateOf(title.ifBlank { context.getString(R.string.webview_loading) }) }
    var showMenu by remember { mutableStateOf(false) }
    data class WebViewErrorState(val errorCode: Int, val description: String, val isHttpError: Boolean = false)
    var webErrorState by remember { mutableStateOf<WebViewErrorState?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileUploadCallback?.let { cb -> cb.onReceiveValue(if (uri != null) arrayOf(uri) else null); fileUploadCallback = null }
    }

    // ── 打开动画 ──
    var hasAppeared by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            hasAppeared = true
            isHiding = false
            heightAnimatable.snapTo(0f)
            heightAnimatable.animateTo(minHeight.value, tween(300)) { sheetHeight = value.dp }
        }
    }

    // ── 关闭：在组合阶段同步检测 visible → false，立即设 isHiding = true ──
    // 这样同一帧内 if (visible || isHiding) 为 true，弹窗不会从组合树移除
    if (!visible && hasAppeared && !isHiding) {
        isHiding = true
    }

    // ── isHiding → 关闭动画（不调用 onDismissRequest，避免循环触发）──
    LaunchedEffect(isHiding) {
        if (isHiding) {
            webViewRef.value?.stopLoading()
            heightAnimatable.snapTo(sheetHeight.value)
            heightAnimatable.animateTo(0f, tween(250)) { sheetHeight = value.dp }
            // 重置状态，确保 if (visible || isHiding) 为 false → 整个组件从组合树移除
            isHiding = false
            hasAppeared = false
        }
    }

    // ── BackHandler（合并为单一）──
    BackHandler(enabled = visible || isHiding) {
        if (canGoBack) {
            webViewRef.value?.goBack()
        } else {
            onDismissRequest()
        }
    }

    // ── Scrim 透明度 ──
    val fraction = ((sheetHeight - minHeight) / (maxHeight - minHeight)).coerceIn(0f, 1f)
    val scrimAlpha = lerp(0.32f, 0.5f, fraction)

    // ── 渲染 ──
    if (visible || isHiding) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim（detectTapGestures 只响应轻触，不拦截拖拽）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDismissRequest() })
                    }
            )

            // Sheet 容器
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 0.dp, max = maxHeight)
                    .height(sheetHeight)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.fillMaxSize()) {
                    // ── 手柄区域 ──
                    // 两层布局：底层可拖拽 Box 覆盖整行，上层按钮叠在上面
                    // 按钮区域外（中间 + 空白区域）都可以上下拖拽
                    val haptic = LocalHapticFeedback.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(handleBarHeight)
                    ) {
                        // 底层：整行可拖拽 + 点击水波纹（DragHandle 居中显示）
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(onClick = {})  // 水波纹视觉反馈
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            with(density) {
                                                sheetHeight = (sheetHeight - dragAmount.y.toDp())
                                                    .coerceIn(minHeight, maxHeight)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                            BottomSheetDefaults.DragHandle()
                        }
                        // 上层：按钮 Row（叠在可拖拽层上方，Compose 事件分发中上层优先接收点击）
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 左侧：返回按钮（仅用于 WebView 历史返回，无历史时禁用）
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                IconButton(
                                    onClick = { webViewRef.value?.goBack() },
                                    enabled = canGoBack
                                ) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.common_back),
                                        tint = if (canGoBack) MaterialTheme.colorScheme.onSurfaceVariant
                                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                                }
                            }
                            // 中间留空（DragHandle 在底层 Box 中显示）
                            Spacer(Modifier.weight(1f))
                            // 右侧：更多菜单
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more_options),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_close)) },
                                        leadingIcon = { Icon(Icons.Outlined.Close, null) },
                                        onClick = { showMenu = false; onDismissRequest() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_refresh)) },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                        onClick = { showMenu = false; webViewRef.value?.reload() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.webview_open_in_browser)) },
                                        leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) },
                                        onClick = {
                                            showMenu = false
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webViewRef.value?.url ?: url)))
                                            } catch (_: ActivityNotFoundException) {
                                                snackbar.show(context.getString(R.string.common_no_browser), ToastUtils.Type.ERROR)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── WebView 内容区 ──
                    Box(Modifier.fillMaxSize()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
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
                                    settings.displayZoomControls = false
                                    settings.userAgentString = UserAgentProvider.getActiveUserAgent()

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, pageUrl, favicon)
                                            isLoading = true; webErrorState = null; canGoBack = view?.canGoBack() == true
                                        }
                                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                            super.onPageFinished(view, pageUrl)
                                            isLoading = false; canGoBack = view?.canGoBack() == true
                                        }
                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true && webErrorState == null) {
                                                isLoading = false
                                                webErrorState = WebViewErrorState(error?.errorCode ?: -1, error?.description?.toString() ?: ctx.getString(R.string.common_unknown_error))
                                            }
                                        }
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val pageUrl = request?.url?.toString() ?: return false
                                            return view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, pageUrl, TAG)
                                        }
                                        override fun doUpdateVisitedHistory(view: WebView?, pageUrl: String?, isReload: Boolean) {
                                            super.doUpdateVisitedHistory(view, pageUrl, isReload); canGoBack = view?.canGoBack() == true
                                        }
                                        @Deprecated("Deprecated in Java") @Suppress("DEPRECATION")
                                        override fun shouldOverrideUrlLoading(view: WebView?, pageUrl: String?): Boolean {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return false
                                            return pageUrl != null && view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, pageUrl, TAG)
                                        }
                                    }

                                    setDownloadListener { downloadUrl, _, _, _, _ ->
                                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))) }
                                        catch (_: ActivityNotFoundException) { snackbar.show(ctx.getString(R.string.webview_no_download_tool), ToastUtils.Type.ERROR) }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress; if (newProgress == 100) isLoading = false
                                        }
                                        override fun onReceivedTitle(view: WebView?, t: String?) {
                                            super.onReceivedTitle(view, t); if (!t.isNullOrBlank()) pageTitle = t
                                        }
                                        override fun onShowFileChooser(wv: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                                            fileUploadCallback = filePathCallback; fileUploadLauncher.launch("*/*"); return true
                                        }
                                    }

                                    webViewRef.value = this
                                    val headers = HashMap<String, String>()
                                    headers["Referer"] = "https://${try { Uri.parse(url).host } catch (_: Exception) { "" }}/"
                                    loadUrl(url, headers)
                                }
                            },
                            update = { webView ->
                                // 保险起见同步 layoutParams 高度
                                val targetHeightPx = with(density) { sheetHeight.roundToPx() }
                                if (webView.layoutParams.height != targetHeightPx) {
                                    webView.layoutParams.height = targetHeightPx
                                    webView.requestLayout()
                                }
                            },
                            onRelease = { webView ->
                                webView.stopLoading(); webView.loadUrl("about:blank")
                                webView.clearHistory(); webView.removeAllViews(); webView.destroy()
                            }
                        )

                        // 进度条
                        if (progress < 100) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }

                        // 错误叠加层
                        webErrorState?.let { error ->
                            WebViewErrorOverlay(
                                errorCode = error.errorCode,
                                description = error.description,
                                isHttpError = error.isHttpError,
                                onRetry = { webErrorState = null; webViewRef.value?.reload() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// lerp 辅助
private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction.coerceIn(0f, 1f)
