package edu.cqwu.electricity.electricity.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.payment.ui.PaymentConfirmScreen
import edu.cqwu.electricity.theme.util.ToastUtils
import edu.cqwu.electricity.webview.util.WebViewUrlUtil

@Composable
fun PaymentSelectionScreen(
    viewModel: RechargeViewModel,
    onBack: () -> Unit,
    onPaymentComplete: () -> Unit,
) {
    val recharge by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current

    // 进入页面时立即创建订单
    LaunchedEffect(Unit) { viewModel.submitRecharge() }

    // 显示支付错误
    LaunchedEffect(recharge.payment.error) {
        recharge.payment.error?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearPaymentState()
        }
    }

    // 显示充值错误
    LaunchedEffect(recharge.createOrderError) {
        recharge.createOrderError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearRechargeState()
        }
    }

    PaymentConfirmScreen(
        title = if (recharge.fullName.isNotBlank()) {
            stringResource(R.string.payment_title_for_user, recharge.fullName)
        } else {
            stringResource(R.string.payment_title_default)
        },
        amount = recharge.selectedAmount ?: recharge.customAmount.toDoubleOrNull(),
        isLoading = recharge.isCreatingOrder,
        isOrderReady = recharge.showselectData != null,
        errorMessage = recharge.createOrderError,
        orderId = recharge.showselectData?.orderId ?: "",
        payment = recharge.payment,
        successUrlPattern = { WebViewUrlUtil.isPaymentSuccessUrl(it) },
        hasTimeout = true,
        onSelectMethod = { viewModel.selectPaymentMethod(it) },
        onSubmitPayment = { viewModel.submitPayment() },
        onRetry = { viewModel.submitRecharge() },
        onBack = { viewModel.clearRechargeState(); onBack() },
        onClearState = { viewModel.clearRechargeState() },
        onPaymentComplete = onPaymentComplete,
        startPolling = { viewModel.startPollingOrderStatus(it) },
    )
}
