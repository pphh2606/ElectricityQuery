package edu.cqwu.electricity.payment.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.payment.data.PaymentMethod
import edu.cqwu.electricity.theme.ui.LoadingDialog
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.toTopAppBarColors

/**
 * 支付确认页面的三个阶段
 *
 * 选择支付方式 → 等待支付完成 → 支付成功
 */
private enum class PaymentPhase {
    /** 选择支付方式，点击确认支付 */
    SELECT_METHOD,
    /** 已提交支付，等待用户在半屏弹窗中完成支付 */
    WAITING_PAYMENT,
    /** 支付成功，显示成功卡片 */
    PAYMENT_SUCCESS,
}

/**
 * 统一支付确认页面
 *
 * 电费充值和校园卡充值共用的支付流程 UI。
 * 三个连续阶段：选择支付方式 → 等待支付完成 → 支付成功。
 *
 * @param title 页面标题
 * @param amount 充值金额（null 时不显示）
 * @param isLoading 订单是否正在创建中
 * @param isOrderReady 订单是否已就绪
 * @param errorMessage 订单创建错误信息
 * @param orderId 订单 ID
 * @param payment 共享支付状态
 * @param successUrlPattern 判断 URL 是否为支付成功回调的函数
 * @param hasTimeout 是否启用支付超时兜底
 * @param onSelectMethod 选择支付方式回调
 * @param onSubmitPayment 提交支付回调
 * @param onRetry 订单创建失败后重试回调
 * @param onBack 返回回调
 * @param onClearState 清除状态回调
 * @param onPaymentComplete 支付完成导航回调
 * @param startPolling 启动订单轮询回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentConfirmScreen(
    title: String,
    amount: Double?,
    isLoading: Boolean,
    isOrderReady: Boolean,
    errorMessage: String?,
    orderId: String,
    payment: PaymentState,
    successUrlPattern: (String) -> Boolean,
    hasTimeout: Boolean,
    onSelectMethod: (PaymentMethod) -> Unit,
    onSubmitPayment: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onClearState: () -> Unit,
    onPaymentComplete: () -> Unit,
    startPolling: (String) -> Unit,
) {
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    // 当前阶段：选择 → 等待 → 成功
    var phase by remember { mutableStateOf(PaymentPhase.SELECT_METHOD) }

    // 半屏弹窗可见性（阶段2 内部控制）
    var showOverlay by remember { mutableStateOf(false) }

    // sbHtml 或 mwebUrl 获取成功 → 自动进入等待阶段并显示弹窗
    LaunchedEffect(payment.sbHtml, payment.mwebUrl) {
        if (!payment.sbHtml.isNullOrBlank() || !payment.mwebUrl.isNullOrBlank()) {
            phase = PaymentPhase.WAITING_PAYMENT
            showOverlay = true
        }
    }

    // 轮询检测到支付完成 → 进入成功阶段
    LaunchedEffect(payment.orderStatus) {
        if (payment.orderStatus == "COMPLETED") {
            phase = PaymentPhase.PAYMENT_SUCCESS
            showOverlay = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isOrderReady) {
                        // ── 金额（始终显示）──
                        Spacer(modifier = Modifier.height(8.dp))
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

                        when (phase) {
                            // ── 阶段1：选择支付方式 ──
                            PaymentPhase.SELECT_METHOD -> {
                                Text(
                                    text = stringResource(R.string.payment_select_method),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                PaymentMethodCard(
                                    method = PaymentMethod.WECHAT,
                                    isSelected = payment.selectedMethod == PaymentMethod.WECHAT,
                                    onClick = { onSelectMethod(PaymentMethod.WECHAT) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                PaymentMethodCard(
                                    method = PaymentMethod.ALIPAY,
                                    isSelected = payment.selectedMethod == PaymentMethod.ALIPAY,
                                    onClick = { onSelectMethod(PaymentMethod.ALIPAY) }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { onSubmitPayment() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    enabled = payment.selectedMethod != null && !payment.isProcessing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (payment.isProcessing) {
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
                            }

                            // ── 阶段2：等待支付完成 ──
                            PaymentPhase.WAITING_PAYMENT -> {
                                PaymentMethodCard(
                                    method = payment.selectedMethod ?: PaymentMethod.ALIPAY,
                                    isSelected = true,
                                    onClick = {},
                                    enabled = false
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        if (!payment.sbHtml.isNullOrBlank() || !payment.mwebUrl.isNullOrBlank()) {
                                            showOverlay = true
                                        } else {
                                            onSubmitPayment()
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    enabled = !payment.isProcessing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (payment.isProcessing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.payment_processing))
                                    } else {
                                        Text(
                                            text = stringResource(R.string.payment_continue),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // ── 阶段3：支付成功 ──
                            PaymentPhase.PAYMENT_SUCCESS -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            stringResource(R.string.payment_success),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        onClearState()
                                        onBack()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.payment_done),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else if (errorMessage != null) {
                        // ── 订单创建失败 ──
                        ReLoginContent(
                            errorMessage = stringResource(R.string.payment_order_failed) + "\n" + errorMessage,
                            requiresReLogin = false,
                            onReLogin = {},
                            onRetry = onRetry,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .padding(vertical = 16.dp),
                        )
                    }
                }

                // ── 支付 WebView 半屏弹窗（阶段2 显示）──
                if (showOverlay
                    && phase == PaymentPhase.WAITING_PAYMENT
                    && (payment.sbHtml != null || payment.mwebUrl != null)
                ) {
                    PaymentOverlay(
                        visible = showOverlay,
                        sbHtml = payment.sbHtml,
                        mwebUrl = payment.mwebUrl,
                        orderId = orderId,
                        orderStatus = payment.orderStatus,
                        startPolling = startPolling,
                        isSuccessUrl = successUrlPattern,
                        hasTimeout = hasTimeout,
                        onClose = { showOverlay = false },
                        onPaymentComplete = { showOverlay = false },
                    )
                }
            }
        }
    }

    // ── 订单创建中：LoadingDialog 阻断式加载（与登录页一致）──
    if (isLoading) {
        LoadingDialog(message = stringResource(R.string.payment_creating_order))
    }
}
