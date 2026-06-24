package edu.cqwu.electricity.ui.cardcenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.paycommom.PaymentConfirmScreen
import edu.cqwu.electricity.util.ToastUtils

private const val CARD_PAY_RETURN_URL_PREFIX = "https://pay.cqwu.edu.cn/PayPreService/"

@Composable
fun CardPaymentScreen(
    viewModel: CardRechargeViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current

    LaunchedEffect(uiState.payment.error) {
        uiState.payment.error?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearPaymentError()
        }
    }

    PaymentConfirmScreen(
        title = if (uiState.cardInfo != null) {
            stringResource(R.string.card_recharge_title_for_user, uiState.cardInfo!!.username)
        } else {
            stringResource(R.string.payment_confirm)
        },
        amount = uiState.orderResult?.let { it.amount / 100.0 },
        isLoading = uiState.isCreatingOrder,
        isOrderReady = uiState.orderResult != null,
        errorMessage = uiState.createOrderError,
        orderId = uiState.orderResult?.orderId ?: "",
        payment = uiState.payment,
        successUrlPattern = { it.startsWith(CARD_PAY_RETURN_URL_PREFIX) },
        hasTimeout = false,
        onSelectMethod = { viewModel.selectPaymentMethod(it) },
        onSubmitPayment = { viewModel.submitPayment() },
        onRetry = { viewModel.createOrder() },
        onBack = { viewModel.clearRechargeState(); onBack() },
        onClearState = { viewModel.clearRechargeState() },
        onPaymentComplete = { /* 仅关闭覆盖层，不导航 */ },
        startPolling = { viewModel.startPollingOrderStatus(it) },
    )
}
