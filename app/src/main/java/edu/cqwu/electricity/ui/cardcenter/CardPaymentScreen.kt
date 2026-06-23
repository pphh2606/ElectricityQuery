package edu.cqwu.electricity.ui.cardcenter

import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.model.PaymentMethod
import edu.cqwu.electricity.data.network.common.UserAgentProvider
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.util.WebViewUrlUtil
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils

private const val TAG = "CardPaymentScreen"
private const val ALIPAY_RETURN_URL_PREFIX = "https://pay.cqwu.edu.cn/PayPreService/aliPayWapBackResReturn"

/**
 * 校园卡支付 — 支付方式选择 + 支付执行页面
 *
 * 支付流程：
 * 1. 显示金额 + 支付方式选择（微信/支付宝）
 * 2. 用户选择后点击"确认支付"
 * 3. 调用 toPayOrderTrade 获取 sbHtml
 * 4. 全屏 WebView 覆盖层加载 sbHtml，表单自动提交到支付宝
 * 5. 用户在 WebView 中与支付宝交互完成支付
 * 6. 拦截回调 URL 判断支付完成 -> 轮询订单状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPaymentScreen(
    viewModel: CardRechargeViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    var showPaymentOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.paymentError) {
        uiState.paymentError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
        }
    }

    // sbHtml 或 mwebUrl 获取成功后自动显示覆盖层
    LaunchedEffect(uiState.sbHtml, uiState.mwebUrl) {
        if (!uiState.sbHtml.isNullOrBlank() || !uiState.mwebUrl.isNullOrBlank()) {
            showPaymentOverlay = true
        }
    }

    val orderResult = uiState.orderResult
    val amount = orderResult?.let { it.amount / 100.0 }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.cardInfo != null) "为 ${uiState.cardInfo!!.username} 充值" else "确认支付",
                            fontWeight = FontWeight.Bold
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
                    colors = topBarColors
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.isCreatingOrder) {
                    Spacer(modifier = Modifier.height(48.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "正在创建订单...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (orderResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    if (amount != null) {
                        Text(
                            text = "充值金额",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "¥ ${"%.2f".format(amount)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "选择支付方式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PaymentMethodCard(
                        method = PaymentMethod.WECHAT,
                        isSelected = uiState.selectedPaymentMethod == PaymentMethod.WECHAT,
                        onClick = { viewModel.selectPaymentMethod(PaymentMethod.WECHAT) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PaymentMethodCard(
                        method = PaymentMethod.ALIPAY,
                        isSelected = uiState.selectedPaymentMethod == PaymentMethod.ALIPAY,
                        onClick = { viewModel.selectPaymentMethod(PaymentMethod.ALIPAY) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.orderStatus == "COMPLETED") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("支付成功！", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.submitPayment() },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = uiState.selectedPaymentMethod != null && !uiState.isPaying && !showPaymentOverlay,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (uiState.isPaying) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("处理中...")
                            } else {
                                Text("确认支付", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (uiState.createOrderError != null) {
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("订单创建失败", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(uiState.createOrderError ?: "未知错误", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.createOrder() }, modifier = Modifier.fillMaxWidth()) { Text("重试") }
                }
            }
        }

        // 全屏 WebView 覆盖层（支付流程）
        if (showPaymentOverlay && (uiState.sbHtml != null || uiState.mwebUrl != null)) {
            CardPaymentOverlay(
                sbHtml = uiState.sbHtml,
                mwebUrl = uiState.mwebUrl,
                viewModel = viewModel,
                onClose = { showPaymentOverlay = false },
                onPaymentComplete = { showPaymentOverlay = false }
            )
        }
    }
}

/**
 * 支付 WebView 全屏覆盖层
 *
 * 全屏可见 WebView 加载 sbHtml（自动提交表单到支付宝），
 * 用户可以在 WebView 中与支付宝页面交互完成支付。
 */
@Composable
private fun CardPaymentOverlay(
    sbHtml: String?,
    mwebUrl: String?,
    viewModel: CardRechargeViewModel,
    onClose: () -> Unit,
    onPaymentComplete: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    var paymentDetected by remember { mutableStateOf(false) }
    var sentToExternalApp by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.orderStatus) {
        if (uiState.orderStatus == "COMPLETED" && !paymentDetected) {
            paymentDetected = true
            onPaymentComplete()
        }
    }

    // 检测从外部应用（微信/支付宝）返回后自动启动订单轮询
    // 参考电费充值 PaymentSelectionScreen 的 LifecycleEventObserver 模式
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sentToExternalApp && !paymentDetected) {
                Log.d(TAG, ">>> 从外部应用返回，开始轮询订单状态")
                val orderId = uiState.orderResult?.orderId
                if (orderId != null) {
                    viewModel.startPollingOrderStatus(orderId)
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
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            Log.d(TAG, "WebView 导航: $url")

                            // ★ 非 http/https 协议必须先于域名检查处理！
                            // alipays://, weixin://, intent:// 等自定义scheme通过Intent启动外部App
                            if (view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, url, TAG)) {
                                Log.d(TAG, "自定义scheme已处理: $url")
                                sentToExternalApp = true
                                return true
                            }

                            // 支付宝同步回调 — 支付完成
                            if (url.startsWith(ALIPAY_RETURN_URL_PREFIX)) {
                                Log.d(TAG, "检测到支付宝回调，支付完成")
                                val orderId = uiState.orderResult?.orderId
                                if (orderId != null) {
                                    viewModel.startPollingOrderStatus(orderId)
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

                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            Log.e(TAG, "WebView 错误: $errorCode $description @ $failingUrl")
                        }
                    }

                    // 根据支付方式选择加载策略
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
    }
}

/**
 * 支付方式卡片
 */
@Composable
private fun PaymentMethodCard(
    method: PaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (method == PaymentMethod.WECHAT) Color(0xFF07C160) else Color(0xFF1677FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (method == PaymentMethod.WECHAT) "微" else "支",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = method.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )

            if (isSelected) {
                Icon(imageVector = Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}
