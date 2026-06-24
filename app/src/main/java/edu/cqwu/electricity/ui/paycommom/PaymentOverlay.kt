package edu.cqwu.electricity.ui.paycommom

import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.model.PaymentMethod
import edu.cqwu.electricity.data.network.common.UserAgentProvider
import edu.cqwu.electricity.util.WebViewUrlUtil
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
) {
    Card(
        onClick = onClick,
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
                .padding(16.dp),
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
                    text = method.displayName.first().toString(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = method.displayName,
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
//  支付 WebView 覆盖层（共享）
// ================================================================

/**
 * 统一支付 WebView 覆盖层
 *
 * 电费充值和校园卡充值共用。
 * 全屏可见 WebView 加载 sbHtml（支付宝自动提交表单）或 mwebUrl（微信 H5 支付页）。
 *
 * @param sbHtml 支付宝自动提交表单 HTML
 * @param mwebUrl 微信 H5 支付页 URL
 * @param orderId 订单 ID（用于轮询）
 * @param orderStatus 当前订单状态（"COMPLETED" 表示支付完成）
 * @param startPolling 启动轮询的回调
 * @param isSuccessUrl 判断 URL 是否为支付成功回调的函数
 * @param hasTimeout 是否启用超时兜底（电费: true, 校园卡: false）
 * @param timeoutMs 超时时间（毫秒），默认 60 秒
 * @param onClose 关闭覆盖层回调（返回按钮点击时调用）
 * @param onPaymentComplete 支付完成回调
 */
@Composable
fun PaymentOverlay(
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
    var paymentDetected by remember { mutableStateOf(false) }
    var sentToExternalApp by remember { mutableStateOf(false) }

    // 订单状态变化检测
    LaunchedEffect(orderStatus) {
        if (orderStatus == "COMPLETED" && !paymentDetected) {
            paymentDetected = true
            onPaymentComplete()
        }
    }

    // 支付确认超时兜底（仅电费启用）
    if (hasTimeout) {
        LaunchedEffect(Unit) {
            delay(timeoutMs)
            if (!paymentDetected) {
                Log.e(TAG, ">>> 支付确认超时（${timeoutMs / 1000}s），自动返回")
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

    // 检测从外部应用（微信/支付宝）返回后自动启动订单轮询
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sentToExternalApp && !paymentDetected) {
                Log.d(TAG, ">>> 从外部应用返回，开始轮询订单状态")
                if (orderId.isNotBlank()) {
                    startPolling(orderId)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
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
                        userAgentString = UserAgentProvider.getActiveUserAgent()
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            Log.d(TAG, "WebView 导航: $url")

                            // 非 http/https 协议 → 外部 App
                            if (view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, url, TAG)) {
                                Log.d(TAG, "自定义 scheme 已处理: $url")
                                sentToExternalApp = true
                                return true
                            }

                            // 检测支付成功回调
                            if (isSuccessUrl(url)) {
                                if (!paymentDetected) {
                                    Log.d(TAG, ">>> 检测到支付成功回调！")
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
                            Log.d(TAG, "页面开始加载: $url")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d(TAG, "页面加载完成: $url")
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            Log.e(TAG, "WebView 错误: ${error?.description} @ ${request?.url}")
                        }
                    }

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
            modifier = Modifier.fillMaxSize()
        )

        // 左上角半透明返回按钮
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.TopStart)
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "关闭", tint = Color.White)
        }
    }
}
