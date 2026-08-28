package edu.cqwu.electricity.cardcenter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.cardcenter.data.CardBasicInfo
import edu.cqwu.electricity.cardcenter.data.CardRechargeApi
import edu.cqwu.electricity.cardcenter.data.CardRechargeOrderResult
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.payment.data.PaymentMethod
import edu.cqwu.electricity.payment.ui.PaymentFlowDelegate
import edu.cqwu.electricity.payment.ui.PaymentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

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

    // 支付流程（共享 PaymentState）
    val payment: PaymentState = PaymentState(),
)

/**
 * 校园卡充值 ViewModel
 *
 * 管理充值全流程：查询卡信息 → 选择金额 → 创建订单 → 选择支付方式 → 提交支付 → 轮询状态
 */
class CardRechargeViewModel(application: Application) : AndroidViewModel(application) {

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
        getString = { getApplication<Application>().getString(it) },
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
            _uiState.update { it.copy(queryError = getApplication<Application>().getString(R.string.error_enter_student_id)) }
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
                        it.copy(isQuerying = false, isRefreshing = false, queryError = e.message ?: getApplication<Application>().getString(R.string.error_query_failed))
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
                        it.copy(isRefreshing = false, queryError = e.message ?: getApplication<Application>().getString(R.string.error_query_failed))
                    }
                }
        }
    }

    /**
     * 从当前登录账号自动填充数字学号并查询校园卡信息。
     *
     * 登录用户名可能是登录别名（非学号），而校园卡系统只认数字学号；
     * 学号在登录时获取并随账号缓存（[AccountSessionStore.getActiveStudentId]，本地读取零网络）。
     *
     * 仅在输入框为空时填充（避免覆盖用户已手动输入的内容）；
     * 未登录或本地无学号（未回填）时静默跳过，不打扰用户。
     */
    fun autoFillStudentIdFromLogin() {
        viewModelScope.launch {
            val studentId = AccountSessionStore.getActiveStudentId()
            if (studentId.isNullOrBlank()) {
                AppLog.d("CardRechargeVM", "autoFillStudentIdFromLogin: 本地无学号（未登录或未回填），跳过自动填充")
                return@launch
            }
            if (_uiState.value.studentId.isBlank()) {
                setStudentId(studentId)
                queryCardInfo()
            }
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
        val amount = getEffectiveAmount()!!

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreatingOrder = true,
                    createOrderError = null,
                    orderResult = null,
                    payment = PaymentState(),
                )
            }
            api.createOrder(PROJECT_ID, String.format(Locale.US, "%.2f", amount))
                .onSuccess { order ->
                    _uiState.update {
                        it.copy(isCreatingOrder = false, orderResult = order)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isCreatingOrder = false, createOrderError = getApplication<Application>().getString(R.string.error_create_order_failed, e.message ?: ""))
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
                payment = PaymentState(),
            )
        }
    }

    fun clearPaymentError() {
        _uiState.update { it.copy(payment = it.payment.copy(error = null)) }
    }
}
