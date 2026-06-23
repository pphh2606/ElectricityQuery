package edu.cqwu.electricity.ui.cardcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.PaymentMethod
import edu.cqwu.electricity.data.network.cardrecharge.CardBasicInfo
import edu.cqwu.electricity.data.network.cardrecharge.CardOrderStatus
import edu.cqwu.electricity.data.network.cardrecharge.CardPaymentResult
import edu.cqwu.electricity.data.network.cardrecharge.CardRechargeOrderResult
import edu.cqwu.electricity.data.repository.CardRechargeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 校园卡充值页面状态
 */
data class CardRechargeUiState(
    // 学号输入
    val studentId: String = "",
    val isQuerying: Boolean = false,
    val queryError: String? = null,

    // 校园卡信息
    val cardInfo: CardBasicInfo? = null,

    // 充值金额
    val selectedAmount: Double? = null,
    val customAmount: String = "",

    // 订单创建
    val isCreatingOrder: Boolean = false,
    val orderResult: CardRechargeOrderResult? = null,
    val createOrderError: String? = null,

    // 导航状态（防止预测性返回手势取消时重复导航）
    val hasNavigatedToPayment: Boolean = false,

    // 支付方式（本地固定）
    val selectedPaymentMethod: PaymentMethod? = null,

    // 支付执行
    val isPaying: Boolean = false,
    val sbHtml: String? = null,
    val mwebUrl: String? = null,
    val paymentError: String? = null,

    // 订单状态轮询
    val orderStatus: String? = null,
    val isPolling: Boolean = false,
)

/**
 * 校园卡充值 ViewModel
 *
 * 管理充值全流程：查询卡信息 → 选择金额 → 创建订单 → 选择支付方式 → 提交支付 → 轮询状态
 */
class CardRechargeViewModel : ViewModel() {

    private val repository = CardRechargeRepository()

    private val _uiState = MutableStateFlow(CardRechargeUiState())
    val uiState: StateFlow<CardRechargeUiState> = _uiState.asStateFlow()

    companion object {
        private const val PROJECT_ID = "80bb5ee2189e4ca2bd5dff4513a0dae2"
    }

    // ================================================================
    //  学号输入与校园卡信息
    // ================================================================

    fun setStudentId(id: String) {
        _uiState.update { it.copy(studentId = id, queryError = null) }
    }

    fun queryCardInfo() {
        val studentId = _uiState.value.studentId.trim()
        if (studentId.isBlank()) {
            _uiState.update { it.copy(queryError = "请输入学号") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isQuerying = true, queryError = null, cardInfo = null) }
            repository.queryBasicInfo(studentId, PROJECT_ID)
                .onSuccess { info ->
                    _uiState.update { it.copy(isQuerying = false, cardInfo = info) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isQuerying = false, queryError = e.message ?: "查询失败")
                    }
                }
        }
    }

    // ================================================================
    //  充值金额
    // ================================================================

    fun selectAmount(amount: Double) {
        _uiState.update { it.copy(selectedAmount = amount, customAmount = "", createOrderError = null) }
    }

    fun setCustomAmount(text: String) {
        _uiState.update { it.copy(customAmount = text, selectedAmount = null, createOrderError = null) }
    }

    private fun getEffectiveAmount(): Double? {
        val state = _uiState.value
        state.selectedAmount?.let { return it }
        val custom = state.customAmount.trim()
        if (custom.isNotBlank()) return custom.toDoubleOrNull()
        return null
    }

    // ================================================================
    //  创建订单
    // ================================================================

    fun createOrder() {
        val amount = getEffectiveAmount()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(createOrderError = "请输入有效金额") }
            return
        }
        val cardInfo = _uiState.value.cardInfo
        if (cardInfo != null && amount > cardInfo.maxBalanceYuan) {
            _uiState.update { it.copy(createOrderError = "金额超出最大余额限制（${cardInfo.maxBalanceYuan}元）") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingOrder = true, createOrderError = null, orderResult = null, hasNavigatedToPayment = false, orderStatus = null, sbHtml = null, paymentError = null, selectedPaymentMethod = null) }
            repository.createOrder(PROJECT_ID, String.format("%.2f", amount))
                .onSuccess { order ->
                    _uiState.update {
                        it.copy(isCreatingOrder = false, orderResult = order, hasNavigatedToPayment = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isCreatingOrder = false, createOrderError = "创建订单失败: ${e.message}")
                    }
                }
        }
    }

    // ================================================================
    //  支付方式选择（本地固定）
    // ================================================================

    fun selectPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(selectedPaymentMethod = method, paymentError = null) }
    }

    // ================================================================
    //  提交支付
    // ================================================================

    fun submitPayment() {
        val orderNo = _uiState.value.orderResult?.orderNo ?: return
        val method = _uiState.value.selectedPaymentMethod ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isPaying = true, paymentError = null, sbHtml = null, mwebUrl = null) }
            repository.toPayOrderTrade(orderNo, method.payType)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(isPaying = false, sbHtml = result.sbHtml, mwebUrl = result.mwebUrl)
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("CardRechargeVM", "支付失败", e)
                    _uiState.update {
                        it.copy(isPaying = false, paymentError = "提交支付失败: ${e.message}")
                    }
                }
        }
    }

    // ================================================================
    //  订单状态轮询
    // ================================================================

    fun startPollingOrderStatus(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPolling = true, orderStatus = null) }
            pollOrderStatus(orderId)
            _uiState.update { it.copy(isPolling = false) }
        }
    }

    private suspend fun pollOrderStatus(orderId: String, timeoutMs: Long = 300_000L) {
        val startTime = System.currentTimeMillis()
        var interval = 2000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val result = repository.queryOrderStatus(orderId)
            val status = result.getOrNull()

            when (status?.status) {
                "COMPLETED" -> {
                    _uiState.update { it.copy(orderStatus = "COMPLETED") }
                    return
                }
                "CLOSED" -> {
                    _uiState.update { it.copy(orderStatus = "CLOSED", paymentError = "订单已关闭") }
                    return
                }
            }

            delay(interval)
            interval = minOf(interval + 1000L, 5000L)
        }

        _uiState.update { it.copy(paymentError = "查询超时，请稍后查看订单状态") }
    }

    // ================================================================
    //  状态清理
    // ================================================================

    /** 标记已导航到支付页面（防止预测性返回手势取消时重复导航） */
    fun markNavigatedToPayment() {
        _uiState.update { it.copy(hasNavigatedToPayment = true) }
    }

    fun clearRechargeState() {
        _uiState.update {
            it.copy(
                studentId = "",
                isQuerying = false,
                queryError = null,
                cardInfo = null,
                selectedAmount = null,
                customAmount = "",
                isCreatingOrder = false,
                orderResult = null,
                createOrderError = null,
                hasNavigatedToPayment = false,
                selectedPaymentMethod = null,
                isPaying = false,
                sbHtml = null,
                mwebUrl = null,
                paymentError = null,
                orderStatus = null,
                isPolling = false,
            )
        }
    }

    fun clearPaymentError() {
        _uiState.update { it.copy(paymentError = null) }
    }
}
