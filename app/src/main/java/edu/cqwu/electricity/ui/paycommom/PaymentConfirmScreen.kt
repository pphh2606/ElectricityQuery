package edu.cqwu.electricity.ui.paycommom

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
import edu.cqwu.electricity.data.model.PaymentMethod
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors

/**
 * 统一支付确认页面
 *
 * 电费充值和校园卡充值共用的支付流程 UI。
 * 包含 Scaffold + TopAppBar、订单创建中/就绪/失败三种状态、
 * 支付方式选择、支付成功卡片、PaymentOverlay。
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
 * @param onClearState 清除状态回调（成功卡片"完成"按钮 / PaymentOverlay 关闭时调用）
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

    // 支付覆盖层可见性
    var showPaymentOverlay by remember { mutableStateOf(false) }

    // sbHtml 或 mwebUrl 获取成功后自动显示覆盖层
    LaunchedEffect(payment.sbHtml, payment.mwebUrl) {
        if (!payment.sbHtml.isNullOrBlank() || !payment.mwebUrl.isNullOrBlank()) {
            showPaymentOverlay = true
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
                    if (isLoading) {
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
                    } else if (isOrderReady) {
                        // 订单创建成功，显示支付方式选择
                        Spacer(modifier = Modifier.height(8.dp))

                        // 金额显示
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
                            isSelected = payment.selectedMethod == PaymentMethod.WECHAT,
                            onClick = { onSelectMethod(PaymentMethod.WECHAT) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 支付宝卡片
                        PaymentMethodCard(
                            method = PaymentMethod.ALIPAY,
                            isSelected = payment.selectedMethod == PaymentMethod.ALIPAY,
                            onClick = { onSelectMethod(PaymentMethod.ALIPAY) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 支付成功提示 + 完成按钮
                        if (payment.orderStatus == "COMPLETED") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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
                                    onPaymentComplete()
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
                        } else {
                            // "确认支付"按钮
                            Button(
                                onClick = onSubmitPayment,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                enabled = payment.selectedMethod != null
                                        && !payment.isProcessing
                                        && !showPaymentOverlay,
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
                    } else if (errorMessage != null) {
                        // 订单创建失败
                        Spacer(modifier = Modifier.height(48.dp))
                        Text(
                            text = stringResource(R.string.payment_order_failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }

                // 支付 WebView 覆盖层（共享组件）
                if (showPaymentOverlay && (payment.sbHtml != null || payment.mwebUrl != null)) {
                    PaymentOverlay(
                        sbHtml = payment.sbHtml,
                        mwebUrl = payment.mwebUrl,
                        orderId = orderId,
                        orderStatus = payment.orderStatus,
                        startPolling = startPolling,
                        isSuccessUrl = successUrlPattern,
                        hasTimeout = hasTimeout,
                        onClose = {
                            showPaymentOverlay = false
                            onClearState()
                        },
                        onPaymentComplete = {
                            showPaymentOverlay = false
                        },
                    )
                }
            }
        }
    }
}
