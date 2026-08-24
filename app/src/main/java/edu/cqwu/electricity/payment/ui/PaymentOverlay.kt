package edu.cqwu.electricity.payment.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import edu.cqwu.electricity.R
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.webview.ui.WebViewHost
import edu.cqwu.electricity.webview.ui.WebViewProgress
import edu.cqwu.electricity.webview.ui.rememberWebViewHostState
import kotlinx.coroutines.delay

private const val TAG = "PaymentOverlay"

/**
 * 统一支付 WebView 半屏弹窗。
 *
 * 基于 BottomSheetDialog / ModalBottomSheet，加载 sbHtml（支付宝自动提交表单）
 * 或 mwebUrl（微信 H5 支付页），并保留支付成功检测、外部 App 返回轮询和超时兜底。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val webViewState = rememberWebViewHostState()
    var paymentDetected by remember { mutableStateOf(false) }
    var sentToExternalApp by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            paymentDetected = false
            sentToExternalApp = false
        }
    }

    // 订单状态变化检测，仅在弹窗可见时生效。
    LaunchedEffect(orderStatus, visible) {
        if (visible && orderStatus == "COMPLETED" && !paymentDetected) {
            paymentDetected = true
            onPaymentComplete()
        }
    }

    // 支付确认超时兜底，仅电费充值启用。
    LaunchedEffect(visible) {
        if (visible && hasTimeout) {
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

    // 从外部应用（微信/支付宝）返回后自动启动订单轮询。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, visible) {
        if (visible) {
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
        } else {
            onDispose {}
        }
    }

    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onClose,
        skipPartiallyExpanded = false,
        onHideStarted = { webViewState.stopLoading() },
        leadingButton = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingButton = {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        contentPadding = PaddingValues(0.dp),
        contentArrangement = Arrangement.Top,
        contentModifier = Modifier.fillMaxHeight(),
    ) {
        Box(Modifier.fillMaxSize()) {
            WebViewHost(
                state = webViewState,
                modifier = Modifier.fillMaxSize(),
                enableZoom = false,
                showZoomControls = false,
                onBeforeLoad = { webView ->
                    if (!sbHtml.isNullOrBlank()) {
                        webView.loadDataWithBaseURL(
                            "https://pay.cqwu.edu.cn/",
                            sbHtml,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                    } else if (!mwebUrl.isNullOrBlank()) {
                        val headers = mapOf("Referer" to "https://pay.cqwu.edu.cn/")
                        webView.loadUrl(mwebUrl, HashMap(headers))
                    }
                },
                onUrlOverride = { _, url ->
                    if (isSuccessUrl(url)) {
                        if (!paymentDetected) {
                            paymentDetected = true
                            onPaymentComplete()
                        }
                        true
                    } else {
                        false
                    }
                },
                onCustomSchemeOpened = { sentToExternalApp = true },
                onPageStarted = { _, url ->
                    AppLog.d(TAG, "页面开始加载: $url")
                },
                onPageFinished = { _, url ->
                    AppLog.d(TAG, "页面加载完成: $url")
                },
                onMainFrameError = { _, error ->
                    AppLog.e(TAG, "WebView 错误: ${error.description} url=${error.requestUrl}")
                },
            )

            WebViewProgress(
                progress = webViewState.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}
