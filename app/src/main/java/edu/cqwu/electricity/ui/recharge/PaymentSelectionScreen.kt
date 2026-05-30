package edu.cqwu.electricity.ui.recharge

import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import edu.cqwu.electricity.util.WebViewUrlUtil
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.model.PaymentMethod
import kotlinx.coroutines.delay

/**
 * 支付方式选择页面
 *
 * 流程：
 * 1. 进入页面后自动调用 submitRecharge() 创建订单
 * 2. 订单创建成功后显示支付方式选择
 * 3. 用户选择支付方式后，点击"确认支付"启动隐藏 WebView 支付流程
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSelectionScreen(
    viewModel: RechargeViewModel,
    onBack: () -> Unit,
    onPaymentComplete: () -> Unit
) {
    val recharge by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    // 支付流程状态
    var isPaymentActive by remember { mutableStateOf(false) }   // WebView 引擎生命周期
    var showPaymentOverlay by remember { mutableStateOf(false) } // 覆盖层 UI 可见性

    // 进入页面时立即创建订单
    LaunchedEffect(Unit) {
        viewModel.submitRecharge()
    }

    // 显示支付错误（使用 ToastOverlay）
    LaunchedEffect(recharge.paymentError) {
        recharge.paymentError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearPaymentState()
        }
    }

    // 显示充值错误（使用 ToastOverlay）
    LaunchedEffect(recharge.rechargeError) {
        recharge.rechargeError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearRechargeState()
        }
    }

    // 动态标题
    val title = if (recharge.fullName.isNotBlank()) {
        stringResource(R.string.payment_title_for_user, recharge.fullName)
    } else {
        stringResource(R.string.payment_title_default)
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearRechargeState()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (recharge.isRecharging) {
                    // 订单创建中
                    Spacer(modifier = Modifier.height(48.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.payment_creating_order),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (recharge.payUrl != null) {
                    // 订单创建成功，显示支付方式选择
                    Spacer(modifier = Modifier.height(8.dp))

                    // 订单信息
                    val amount = recharge.selectedAmount
                        ?: recharge.customAmount.toDoubleOrNull()
                    if (amount != null) {
                        Text(
                            text = stringResource(R.string.payment_amount_label),
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
                        text = stringResource(R.string.payment_select_method),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 微信支付卡片
                    PaymentMethodCard(
                        method = PaymentMethod.WECHAT,
                        isSelected = recharge.selectedPaymentMethod == PaymentMethod.WECHAT,
                        onClick = { viewModel.selectPaymentMethod(PaymentMethod.WECHAT) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 支付宝卡片
                    PaymentMethodCard(
                        method = PaymentMethod.ALIPAY,
                        isSelected = recharge.selectedPaymentMethod == PaymentMethod.ALIPAY,
                        onClick = { viewModel.selectPaymentMethod(PaymentMethod.ALIPAY) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // "确认支付"按钮 - 启动隐藏 WebView 支付流程
                    Button(
                        onClick = {
                            isPaymentActive = true
                            showPaymentOverlay = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = recharge.selectedPaymentMethod != null && !recharge.isProcessingPayment,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (recharge.isProcessingPayment) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.payment_processing))
                        } else {
                            Text(
                                text = stringResource(R.string.payment_confirm),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (recharge.rechargeError != null) {
                    // 订单创建失败
                    Spacer(modifier = Modifier.height(48.dp))
                    Text(
                        text = stringResource(R.string.payment_order_failed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = recharge.rechargeError ?: stringResource(R.string.common_unknown_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.submitRecharge() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }

            // 支付 WebView 覆盖层（全屏），isPaymentActive 控制引擎生命周期
            if (isPaymentActive && recharge.payUrl != null && recharge.selectedPaymentMethod != null) {
                PaymentWebViewOverlay(
                    show = showPaymentOverlay,
                    payUrl = recharge.payUrl!!,
                    selectedMethod = recharge.selectedPaymentMethod!!,
                    viewModel = viewModel,
                    onClose = {
                        isPaymentActive = false
                        showPaymentOverlay = false
                        viewModel.clearPaymentState()
                        viewModel.clearRechargeState()
                        onBack()
                    },
                    onPaymentComplete = {
                        isPaymentActive = false
                        showPaymentOverlay = false
                        viewModel.clearPaymentState()
                        viewModel.clearRechargeState()
                        onPaymentComplete()
                    },
                    onShowToast = { msg, type -> snackbar.show(msg, type) }
                )
            }
        }
    }
    }
}

// ================================================================
//  支付 WebView 覆盖层
// ================================================================

private const val TAG_OVERLAY = "PaymentOverlay"

/**
 * 覆盖层显示阶段
 */
private enum class OverlayPhase {
    LOADING,        // 加载覆盖层显示中（计时器）
    EXTERNAL_APP,   // 外部应用已打开，覆盖层隐藏
    WEBVIEW         // WebView 可见（从外部应用返回后）
}

/**
 * 支付 WebView 覆盖层
 *
 * 管理隐藏 WebView → JS 注入 → gotToPay → mwebUrl → 微信支付 → 轮询 → returnUrl → CAS 的完整流程。
 *
 * 核心作用：在同一个 WebView 中完成 showselect 页面加载、gotToPay AJAX 调用、
 * mwebUrl 导航和 returnUrl/CAS 认证，保持完整的浏览器会话（Cookie）连续性，
 * 解决 OkHttp 与 WebView 之间会话断裂导致 CAS 认证失败的问题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentWebViewOverlay(
    show: Boolean,
    payUrl: String,
    selectedMethod: PaymentMethod,
    viewModel: RechargeViewModel,
    onClose: () -> Unit,
    onPaymentComplete: () -> Unit,
    onShowToast: (String, ToastUtils.Type) -> Unit
) {
    LocalContext.current

    // 状态
    var phase by remember { mutableStateOf(OverlayPhase.LOADING) }
    var statusText by remember { mutableStateOf("正在初始化支付...") }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    val engineRef = remember { mutableStateOf<PaymentWebViewEngine?>(null) }
    var isNavigatingToReturnUrl by remember { mutableStateOf(false) }
    var paymentSuccessDetected by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableStateOf(0) }

    // 从 showselect URL 中提取 orderId
    // showselect URL 格式: https://pay.cqwu.edu.cn/PayPreService/showselect?sign=...&orderId=...
    val orderId = remember {
        try {
            val uri = Uri.parse(payUrl)
            val id = uri.getQueryParameter("orderId")
            Log.d(TAG_OVERLAY, "从 URL 提取 orderId: $id")
            id ?: ""
        } catch (e: Exception) {
            Log.e(TAG_OVERLAY, "提取 orderId 失败: ${e.message}")
            ""
        }
    }

    // 轮询订单状态
    // 模拟 showselect 页面的 JS setInterval(queryOrderStatus, 1500)
    LaunchedEffect(orderId) {
        if (orderId.isNotBlank()) {
            Log.d(TAG_OVERLAY, "开始轮询订单状态: orderId=$orderId")
            while (true) {
                delay(1500)

                // 如果已经关闭了覆盖层，停止轮询
                if (!show) break

                val result = viewModel.queryPaymentOrderStatus(orderId)
                result.onSuccess { response ->
                    val status = response.data?.status
                    val returnUrl = response.data?.returnUrl
                    Log.d(TAG_OVERLAY, "轮询订单状态: orderId=$orderId, status=$status")

                    if (status == "COMPLETED") {
                        Log.d(TAG_OVERLAY, ">>> 订单已完成！准备处理 returnUrl")

                        val targetUrl = if (!returnUrl.isNullOrBlank()) {
                            returnUrl
                        } else {
                            // 无 returnUrl 时，认为支付已成功
                            Log.d(TAG_OVERLAY, ">>> returnUrl 为空，支付已完成")
                            onPaymentComplete()
                            break
                        }

                        Log.d(TAG_OVERLAY, ">>> WebView 导航到 returnUrl: $targetUrl")
                        isNavigatingToReturnUrl = true
                        statusText = "支付成功，正在进行到账处理..."

                        // 让 WebView 加载 returnUrl
                        // returnUrl 会触发 CAS 认证，完成后分配资金到账
                        webViewRef.value?.loadUrl(targetUrl)

                        // 等待 CAS 认证流程完成（最长 15 秒）
                        var waited = 0
                        while (isNavigatingToReturnUrl && waited < 15) {
                            delay(1000)
                            waited++
                        }

                        if (isNavigatingToReturnUrl) {
                            Log.d(TAG_OVERLAY, ">>> returnUrl 导航超时（30s），直接返回 Dashboard")
                            isNavigatingToReturnUrl = false
                        } else {
                            Log.d(TAG_OVERLAY, ">>> CAS 认证完成，返回 Dashboard")
                        }

                        onPaymentComplete()
                        break
                    }
                }.onFailure { e ->
                    Log.e(TAG_OVERLAY, "轮询订单状态失败: ${e.localizedMessage}")
                }
            }
        } else {
            // 没有 orderId，可能是因为 URL 格式不对
            Log.e(TAG_OVERLAY, "无法提取 orderId，跳过轮询")
            statusText = "订单号无效，请重试"
        }
    }

    // showselect 页面加载超时检测（30 秒）
    LaunchedEffect(show) {
        if (show && phase == OverlayPhase.LOADING) {
            delay(30000)
            if (phase == OverlayPhase.LOADING) {
                Log.e(TAG_OVERLAY, "showselect 页面处理超时（30s）")
                statusText = "支付处理超时，请重试"
            }
        }
    }

    // 支付成功确认超时检测（30 秒兜底）
    // 如果 WebView 页面跳转到成功确认 URL 后 onPaymentComplete() 没有关闭覆盖层，自动关闭
    LaunchedEffect(show) {
        if (show) {
            delay(30000)
            if (!paymentSuccessDetected) {
                Log.e(TAG_OVERLAY, ">>> 支付确认超时（30s），自动返回 Dashboard")
                statusText = "订单超时！在对应寝室查询是否有充值记录"
                onShowToast("订单超时！在对应寝室查询是否有充值记录", ToastUtils.Type.ERROR)
                onPaymentComplete()
            }
        }
    }

    // 秒级计时器：加载覆盖层中显示经过秒数
    LaunchedEffect(show) {
        elapsedSeconds = 0
        while (show && phase == OverlayPhase.LOADING) {
            delay(1000)
            elapsedSeconds++
        }
    }

    // 检测从外部应用返回 → 切换到 WebView 可见模式
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && phase == OverlayPhase.EXTERNAL_APP) {
                Log.d(TAG_OVERLAY, ">>> 从外部应用返回，切换到 WebView 可见模式")
                phase = OverlayPhase.WEBVIEW
                statusText = "支付成功，正在进行到账处理..."
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 全屏覆盖层
    Box(modifier = Modifier.fillMaxSize()) {
        // WebView（隐藏或可见）
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.TRANSPARENT)

                    // 创建 PaymentWebViewEngine
                    val engine = PaymentWebViewEngine(this)
                    engineRef.value = engine
                    webViewRef.value = this

                    // 设置回调
                    engine.onShowselectPageReady = {
                        Log.d(TAG_OVERLAY, ">>> showselect 页面就绪，开始注入 JS")
                        statusText = "正在选择支付方式..."
                        // 注入 JS：选择 radio + 触发 #next.click()
                        engine.injectAndSubmit(selectedMethod)
                    }

                    engine.onMwebUrlDetected = { mwebUrl ->
                        Log.d(TAG_OVERLAY, ">>> 检测到 mwebUrl")
                        Log.d(TAG_OVERLAY, "mwebUrl: ${mwebUrl.take(100)}...")
                        statusText = "正在跳转支付页面..."
                        // 不切换可见性，等待从外部应用返回后再显示 WebView
                    }

                    engine.onWechatIntentDetected = { intentUrl ->
                        Log.d(TAG_OVERLAY, ">>> 拦截到支付协议: $intentUrl")
                        statusText = "请在外部应用中完成支付..."
                        phase = OverlayPhase.EXTERNAL_APP
                    }

                    engine.onNavigationChanged = { url ->
                        Log.d(TAG_OVERLAY, "导航: ${url.take(100)}")

                        // 检测是否已跳转到电量系统页面（充值成功确认）
                        // WebView 页面自己的 JS 轮询检测到 COMPLETED 后会自动导航到 returnUrl
                        // 导航到 electricitypay 主域且非 authserver 即表示充值成功
                        if (WebViewUrlUtil.isPaymentSuccessUrl(url)) {
                            if (!paymentSuccessDetected) {
                                Log.d(TAG_OVERLAY, ">>> 检测到充值成功确认页面！")
                                paymentSuccessDetected = true
                                statusText = "充值成功！"
                                val amount = viewModel.uiState.value.selectedAmount
                                    ?: viewModel.uiState.value.customAmount.toDoubleOrNull()
                                val amountText = if (amount != null) {
                                    "已充值${"%.2f".format(amount)}元"
                                } else {
                                    "充值成功"
                                }
                                onShowToast("${amountText}，可在对应寝室查询充值记录", ToastUtils.Type.SUCCESS)
                                onPaymentComplete()
                            }
                        }

                        // 保留原有检测（兼容通过轮询 + loadUrl 的路径）
                        if (isNavigatingToReturnUrl) {
                            if (WebViewUrlUtil.isPaymentSuccessUrl(url)) {
                                Log.d(TAG_OVERLAY, ">>> 检测到返回电量系统页面，CAS 认证完成")
                                isNavigatingToReturnUrl = false
                            }
                        }
                    }

                    engine.onError = { error ->
                        Log.e(TAG_OVERLAY, "WebView 错误: $error")
                        statusText = "加载出错: $error"
                    }

                    // 初始化：加载 showselect 页面
                    Log.d(TAG_OVERLAY, "=== 初始化 PaymentWebViewOverlay ===")
                    Log.d(TAG_OVERLAY, "payUrl: $payUrl")
                    Log.d(TAG_OVERLAY, "selectedMethod: ${selectedMethod.displayName}")
                    Log.d(TAG_OVERLAY, "orderId: $orderId")
                    engine.initialize(payUrl, orderId)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 根据阶段显示不同 UI
        when (phase) {
            OverlayPhase.LOADING -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${elapsedSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OverlayPhase.EXTERNAL_APP -> {
                // 外部应用中，覆盖层隐藏
            }

            OverlayPhase.WEBVIEW -> {
                IconButton(
                    onClick = {
                        // 关闭支付流程
                        Log.d(TAG_OVERLAY, "用户手动关闭支付覆盖层")
                        engineRef.value?.destroy()
                        onClose()
                    },
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .size(40.dp)
                        .background(
                            color = ComposeColor.Black.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.payment_close),
                        tint = ComposeColor.White
                    )
                }
            }
        }
    }
}

// ================================================================
//  支付方式选择卡片
// ================================================================

/**
 * 支付方式选择卡片
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
                        color = if (method == PaymentMethod.WECHAT) ComposeColor(0xFF07C160)
                                else ComposeColor(0xFF1677FF),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = method.displayName.first().toString(),
                    color = ComposeColor.White,
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
                Text(
                    text = if (method == PaymentMethod.WECHAT) stringResource(R.string.payment_wechat_desc) else stringResource(R.string.payment_alipay_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 选中标记
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.payment_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
