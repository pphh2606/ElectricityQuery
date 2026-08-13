package edu.cqwu.electricity.payment.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import edu.cqwu.electricity.logging.AppLog
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.cqwu.electricity.R
import edu.cqwu.electricity.payment.data.PaymentMethod
import edu.cqwu.electricity.login.data.UserAgentProvider
import edu.cqwu.electricity.webview.util.WebViewUrlUtil
import kotlinx.coroutines.delay

private const val TAG = "PaymentOverlay"

// ================================================================
//  支付方式选择卡片（共享）
// ================================================================

/**
 * 支付方式选择卡片
 *
 * 电费充值和校园卡充值共用。
 *
 * @param method 支付方式
 * @param isSelected 是否选中
 * @param onClick 点击回调
 * @param showDescription 是否显示支付方式描述文字（电费: true, 校园卡: false）
 */
@Composable
fun PaymentMethodCard(
    method: PaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
    showDescription: Boolean = true,
    enabled: Boolean = true,
) {
    Card(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .then(if (!enabled) Modifier.alpha(0.5f) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 支付方式品牌标识（彩色圆形 + 品牌缩写）
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (method == PaymentMethod.WECHAT) Color(0xFF07C160)
                                else Color(0xFF1677FF),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                        text = stringResource(method.labelRes).first().toString(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = stringResource(method.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                if (showDescription) {
                    Text(
                        text = if (method == PaymentMethod.WECHAT) stringResource(R.string.payment_wechat_desc) else stringResource(R.string.payment_alipay_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 选中标记
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(R.string.payment_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ================================================================
//  支付 WebView 半屏弹窗（共享）
// ================================================================

/**
 * 统一支付 WebView 半屏弹窗
 *
 * 电费充值和校园卡充值共用。
 * 底部半屏弹窗样式，可拖拽至全屏，加载 sbHtml（支付宝自动提交表单）或 mwebUrl（微信 H5 支付页）。
 *
 * @param visible 控制弹窗显隐
 * @param sbHtml 支付宝自动提交表单 HTML
 * @param mwebUrl 微信 H5 支付页 URL
 * @param orderId 订单 ID（用于轮询）
 * @param orderStatus 当前订单状态（"COMPLETED" 表示支付完成）
 * @param startPolling 启动轮询的回调
 * @param isSuccessUrl 判断 URL 是否为支付成功回调的函数
 * @param hasTimeout 是否启用超时兜底（电费: true, 校园卡: false）
 * @param timeoutMs 超时时间（毫秒），默认 60 秒
 * @param onClose 关闭覆盖层回调
 * @param onPaymentComplete 支付完成回调
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaymentOverlay(
    visible: Boolean,
    sbHtml: String?,
    mwebUrl: String?,
    orderId: String,
    orderStatus: String?,
    startPolling: (String) -> Unit,
    isSuccessUrl: (String) -> Boolean,
    hasTimeout: Boolean = false,
    timeoutMs: Long = 60_000L,
    onClose: () -> Unit,
    onPaymentComplete: () -> Unit,
) {
    val density = LocalDensity.current

    // ── 尺寸计算 ──
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val minHeight = screenHeight * 0.5f
    val handleBarHeight = 60.dp

    // ── 高度状态（初始为 0，由打开动画从 0 到 minHeight）──
    val heightAnimatable = remember { Animatable(0f) }
    var sheetHeight by remember { mutableStateOf(0.dp) }

    // ── isHiding 模式（退出动画）──
    var isHiding by remember { mutableStateOf(false) }

    // ── WebView 状态 ──
    var progress by remember { mutableIntStateOf(10) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // ── 支付状态 ──
    var paymentDetected by remember { mutableStateOf(false) }
    var sentToExternalApp by remember { mutableStateOf(false) }

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

    // ── 关闭：同步检测 visible → false ──
    if (!visible && hasAppeared && !isHiding) {
        isHiding = true
    }

    // ── 关闭函数 ──
    fun dismiss() {
        if (!isHiding && hasAppeared) {
            isHiding = true
        }
    }

    // ── isHiding → 关闭动画 ──
    LaunchedEffect(isHiding) {
        if (isHiding) {
            webViewRef.value?.stopLoading()
            heightAnimatable.snapTo(sheetHeight.value)
            heightAnimatable.animateTo(0f, tween(250)) { sheetHeight = value.dp }
            isHiding = false
            hasAppeared = false
            onClose()
        }
    }

    // ── BackHandler ──
    BackHandler(enabled = hasAppeared || isHiding) {
        dismiss()
    }

    // ── 订单状态变化检测 ──
    LaunchedEffect(orderStatus) {
        if (orderStatus == "COMPLETED" && !paymentDetected) {
            paymentDetected = true
            onPaymentComplete()
        }
    }

    // ── 支付确认超时兜底（仅电费启用）──
    if (hasTimeout) {
        LaunchedEffect(Unit) {
            delay(timeoutMs)
            if (!paymentDetected) {
                AppLog.e(TAG, ">>> 支付确认超时（${timeoutMs / 1000}s），自动返回")
                if (orderId.isNotBlank()) {
                    startPolling(orderId)
                }
                delay(5000)
                if (!paymentDetected) {
                    onPaymentComplete()
                }
            }
        }
    }

    // ── 检测从外部应用（微信/支付宝）返回后自动启动订单轮询 ──
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sentToExternalApp && !paymentDetected) {
                AppLog.d(TAG, ">>> 从外部应用返回，开始轮询订单状态")
                if (orderId.isNotBlank()) {
                    startPolling(orderId)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Scrim 透明度 ──
    val fraction = ((sheetHeight - minHeight) / (screenHeight - minHeight)).coerceIn(0f, 1f)
    val scrimAlpha = lerp(fraction)

    // ── 渲染 ──
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim（detectTapGestures 只响应轻触，不拦截拖拽）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { dismiss() })
                }
        )

        // Sheet 容器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .heightIn(min = 0.dp, max = screenHeight)
                .height(sheetHeight)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxSize()) {
                // ── 手柄区域 ──
                val haptic = LocalHapticFeedback.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(handleBarHeight)
                ) {
                    // 底层：整行可拖拽
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = {})
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        with(density) {
                                            sheetHeight = (sheetHeight - dragAmount.y.toDp())
                                                .coerceIn(minHeight, screenHeight)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }
                    // 上层：按钮 Row
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧：关闭按钮
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            IconButton(onClick = { dismiss() }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = stringResource(R.string.common_back),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // 右侧：关闭按钮
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            IconButton(onClick = { dismiss() }) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

                                settings.apply {
                                    javaScriptEnabled = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    domStorageEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    userAgentString = UserAgentProvider.getActiveUserAgent()
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        AppLog.d(TAG, "WebView 导航: $url")

                                        // 非 http/https 协议 → 外部 App
                                        if (view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, url, TAG)) {
                                            AppLog.d(TAG, "自定义 scheme 已处理: $url")
                                            sentToExternalApp = true
                                            return true
                                        }

                                        // 检测支付成功回调
                                        if (isSuccessUrl(url)) {
                                            if (!paymentDetected) {
                                                AppLog.d(TAG, ">>> 检测到支付成功回调！")
                                                paymentDetected = true
                                                onPaymentComplete()
                                            }
                                            return true
                                        }

                                        return false
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        if (url == null) return false
                                        if (view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, url, TAG)) {
                                            sentToExternalApp = true
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        AppLog.d(TAG, "页面开始加载: $url")
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        AppLog.d(TAG, "页面加载完成: $url")
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        val description =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error?.description else null
                                        AppLog.e(TAG, "WebView 错误: $description @ ${request?.url}")
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                    }
                                }

                                webViewRef.value = this

                                // 根据支付方式加载页面
                                if (!sbHtml.isNullOrBlank()) {
                                    // 支付宝：加载 HTML 表单，自动提交到支付宝
                                    loadDataWithBaseURL("https://pay.cqwu.edu.cn/", sbHtml, "text/html", "UTF-8", null)
                                } else if (!mwebUrl.isNullOrBlank()) {
                                    // 微信：加载 mwebUrl，页面会跳转到 weixin:// 被 WebViewClient 拦截并打开微信
                                    val headers = mapOf("Referer" to "https://pay.cqwu.edu.cn/")
                                    loadUrl(mwebUrl, HashMap(headers))
                                }
                            }
                        },
                        update = { webView ->
                            val targetHeightPx = with(density) { sheetHeight.roundToPx() }
                            if (webView.layoutParams.height != targetHeightPx) {
                                webView.layoutParams.height = targetHeightPx
                                webView.requestLayout()
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

                    // 进度条
                    if (progress < 100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// lerp 辅助
private fun lerp(fraction: Float): Float = 0.32f + (0.5f - 0.32f) * fraction.coerceIn(0f, 1f)
