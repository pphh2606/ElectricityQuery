package edu.cqwu.electricity.ui.cardcenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.PaymentMethod
import edu.cqwu.electricity.data.network.pay.cardrecharge.CardBasicInfo
import edu.cqwu.electricity.data.network.pay.cardrecharge.CardRechargeApi
import edu.cqwu.electricity.data.network.pay.cardrecharge.CardRechargeOrderResult
import edu.cqwu.electricity.ui.paycommom.PaymentFlowDelegate
import edu.cqwu.electricity.ui.paycommom.PaymentState
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
    val isRefreshing: Boolean = false,
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

    // 支付流程（共享 PaymentState）
    val payment: PaymentState = PaymentState(),
)

/**
 * 校园卡充值 ViewModel
 *
 * 管理充值全流程：查询卡信息 → 选择金额 → 创建订单 → 选择支付方式 → 提交支付 → 轮询状态
 */
class CardRechargeViewModel : ViewModel() {

    private val api = CardRechargeApi()

    private val _uiState = MutableStateFlow(CardRechargeUiState())
    val uiState: StateFlow<CardRechargeUiState> = _uiState.asStateFlow()

    /** 支付流程委托，封装与 RechargeViewModel 共有的支付/金额逻辑 */
    private val paymentFlowDelegate = PaymentFlowDelegate(
        scope = viewModelScope,
        getPaymentState = { _uiState.value.payment },
        updatePayment = { transform -> _uiState.update { it.copy(payment = it.payment.transform()) } },
        getSelectedAmount = { _uiState.value.selectedAmount },
        updateAmount = { selectedAmount, customAmount ->
            _uiState.update { it.copy(selectedAmount = selectedAmount, customAmount = customAmount) }
        },
        getCustomAmount = { _uiState.value.customAmount },
        clearOrderError = { _uiState.update { it.copy(createOrderError = null) } },
    )

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
            _uiState.update { it.copy(isQuerying = true, isRefreshing = true, queryError = null, cardInfo = null) }
            api.queryBasicInfo(studentId, PROJECT_ID)
                .onSuccess { info ->
                    _uiState.update { it.copy(isQuerying = false, isRefreshing = false, cardInfo = info) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isQuerying = false, isRefreshing = false, queryError = e.message ?: "查询失败")
                    }
                }
        }
    }

    /**
     * 下拉刷新：重新查询校园卡信息
     */
    fun refreshCardInfo() {
        val studentId = _uiState.value.studentId.trim()
        if (studentId.isBlank()) {
            _uiState.update { it.copy(isRefreshing = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, queryError = null) }
            api.queryBasicInfo(studentId, PROJECT_ID)
                .onSuccess { info ->
                    _uiState.update { it.copy(isRefreshing = false, cardInfo = info) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, queryError = e.message ?: "查询失败")
                    }
                }
        }
    }

    /**
     * 从当前登录用户自动填充学号并查询校园卡信息。
     * 仅在输入框为空时填充（避免覆盖用户已手动输入的内容）。
     */
    fun autoFillFromLogin(loggedInStudentId: String?) {
        if (loggedInStudentId.isNullOrBlank()) return
        if (_uiState.value.studentId.isBlank()) {
            setStudentId(loggedInStudentId)
            queryCardInfo()
        }
    }

    // ================================================================
    //  充值金额
    // ================================================================

    fun selectAmount(amount: Double) = paymentFlowDelegate.selectAmount(amount)

    fun setCustomAmount(text: String) = paymentFlowDelegate.setCustomAmount(text)

    fun getEffectiveAmount(): Double? = paymentFlowDelegate.getEffectiveAmount()

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
            _uiState.update {
                it.copy(
                    isCreatingOrder = true,
                    createOrderError = null,
                    orderResult = null,
                    hasNavigatedToPayment = false,
                    payment = PaymentState(),
                )
            }
            api.createOrder(PROJECT_ID, String.format("%.2f", amount))
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

    fun selectPaymentMethod(method: PaymentMethod) = paymentFlowDelegate.selectPaymentMethod(method)

    // ================================================================
    //  提交支付
    // ================================================================

    fun submitPayment() {
        paymentFlowDelegate.submitPayment(
            getOrderNo = { _uiState.value.orderResult?.orderNo },
            executePayment = { orderNo ->
                val method = _uiState.value.payment.selectedMethod!!
                val result = api.toPayOrderTrade(orderNo, method.payType).getOrThrow()
                Pair(result.sbHtml, result.mwebUrl)
            },
        )
    }

    // ================================================================
    //  订单状态轮询
    // ================================================================

    fun startPollingOrderStatus(orderId: String) {
        paymentFlowDelegate.startPollingOrderStatus(
            orderId = orderId,
            queryStatus = { id ->
                val result = api.queryOrderStatus(id)
                result.getOrNull()?.status
            },
        )
    }

    // ================================================================
    //  状态清理
    // ================================================================

    /** 标记已导航到支付页面（防止预测性返回手势取消时重复导航） */
    fun markNavigatedToPayment() {
        _uiState.update { it.copy(hasNavigatedToPayment = true) }
    }

    fun clearQueryError() {
        _uiState.update { it.copy(queryError = null) }
    }

    fun clearCreateOrderError() {
        _uiState.update { it.copy(createOrderError = null) }
    }

    fun clearRechargeState() {
        _uiState.update {
            it.copy(
                studentId = "",
                isQuerying = false,
                isRefreshing = false,
                queryError = null,
                cardInfo = null,
                selectedAmount = null,
                customAmount = "",
                isCreatingOrder = false,
                orderResult = null,
                createOrderError = null,
                hasNavigatedToPayment = false,
                payment = PaymentState(),
            )
        }
    }

    fun clearPaymentError() {
        _uiState.update { it.copy(payment = it.payment.copy(error = null)) }
    }
}
