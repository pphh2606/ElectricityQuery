package edu.cqwu.electricity.ui.paycommom

import androidx.annotation.StringRes
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.model.PaymentMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 支付流程委托
 *
 * 封装 [RechargeViewModel][edu.cqwu.electricity.ui.recharge.RechargeViewModel] 和
 * [CardRechargeViewModel][edu.cqwu.electricity.ui.cardcenter.CardRechargeViewModel]
 * 共有的支付流程逻辑：支付方式选择、提交支付、订单状态轮询、金额选择。
 *
 * 通过函数参数/lambda 抽象两个 ViewModel 之间的差异：
 * - 订单号来源不同
 * - 支付 API 不同
 * - 订单状态查询 API 不同
 * - UiState 中金额字段的更新方式不同
 *
 * @param scope 协程作用域（通常传 ViewModel.viewModelScope）
 * @param getPaymentState 获取当前 [PaymentState] 的 lambda
 * @param updatePayment 更新 [PaymentState] 的 lambda，接收一个转换函数
 * @param getSelectedAmount 获取当前选中金额的 lambda
 * @param updateAmount 更新金额字段的 lambda（selectedAmount 和 customAmount）
 * @param clearOrderError 清除订单相关错误的 lambda
 */
class PaymentFlowDelegate(
    private val scope: CoroutineScope,
    private val getPaymentState: () -> PaymentState,
    private val updatePayment: (PaymentState.() -> PaymentState) -> Unit,
    private val getSelectedAmount: () -> Double?,
    private val updateAmount: (selectedAmount: Double?, customAmount: String) -> Unit,
    private val getCustomAmount: () -> String,
    private val clearOrderError: () -> Unit,
    private val getString: (Int) -> String,
) {

    // ================================================================
    //  支付方式选择
    // ================================================================

    /**
     * 选择支付方式
     */
    fun selectPaymentMethod(method: PaymentMethod) {
        updatePayment { copy(selectedMethod = method, error = null) }
    }

    // ================================================================
    //  提交支付
    // ================================================================

    /**
     * 提交支付请求
     *
     * @param getOrderNo 获取订单号的 lambda，返回 null 则中止
     * @param executePayment 执行支付 API 调用的 lambda，返回 Pair(sbHtml, mwebUrl)
     * @param onComplete 支付提交成功后的回调（用于触发后续流程如状态轮询）
     */
    fun submitPayment(
        getOrderNo: () -> String?,
        executePayment: suspend (String) -> Pair<String?, String?>,
        onComplete: () -> Unit = {},
    ) {
        val orderNo = getOrderNo() ?: return
        val payType = getPaymentState().selectedMethod?.payType ?: return

        updatePayment { copy(isProcessing = true, error = null, sbHtml = null, mwebUrl = null) }
        scope.launch {
            try {
                val (sbHtml, mwebUrl) = executePayment(orderNo)
                updatePayment {
                    copy(isProcessing = false, sbHtml = sbHtml, mwebUrl = mwebUrl)
                }
                onComplete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updatePayment {
                    copy(isProcessing = false, error = getString(R.string.error_submit_payment_failed).format(e.message ?: ""))
                }
            }
        }
    }

    // ================================================================
    //  订单状态轮询
    // ================================================================

    /**
     * 启动订单状态轮询
     *
     * @param orderId 订单 ID
     * @param queryStatus 查询订单状态的 lambda，返回状态字符串（"COMPLETED"/"CLOSED"/其他）
     */
    fun startPollingOrderStatus(
        orderId: String,
        queryStatus: suspend (String) -> String?,
    ) {
        scope.launch {
            updatePayment { copy(isPolling = true, orderStatus = null) }
            try {
                pollOrderStatus(orderId, queryStatus)
            } finally {
                updatePayment { copy(isPolling = false) }
            }
        }
    }

    /**
     * 轮询订单状态（内部方法）
     *
     * 每隔 2~5 秒查询一次，最长轮询 5 分钟。
     * COMPLETED/CLOSED 状态立即返回，超时则设置错误信息。
     */
    private suspend fun pollOrderStatus(
        orderId: String,
        queryStatus: suspend (String) -> String?,
    ) {
        val startTime = System.currentTimeMillis()
        val timeoutMs = 300_000L
        var interval = 2000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val status = queryStatus(orderId)

            when (status) {
                "COMPLETED" -> {
                    updatePayment { copy(orderStatus = "COMPLETED") }
                    return
                }
                "CLOSED" -> {
                    updatePayment { copy(orderStatus = "CLOSED", error = getString(R.string.error_order_closed)) }
                    return
                }
            }

            delay(interval)
            interval = minOf(interval + 1000L, 5000L)
        }

        updatePayment { copy(error = getString(R.string.error_query_timeout)) }
    }

    // ================================================================
    //  支付状态清理
    // ================================================================

    /**
     * 清除支付状态，重置为初始值
     */
    fun clearPaymentState() {
        updatePayment { PaymentState() }
    }

    // ================================================================
    //  金额选择
    // ================================================================

    /**
     * 选择预设金额
     */
    fun selectAmount(amount: Double) {
        updateAmount(amount, "")
        clearOrderError()
    }

    /**
     * 设置自定义金额
     */
    fun setCustomAmount(text: String) {
        updateAmount(null, text)
        clearOrderError()
    }

    /**
     * 获取当前有效金额
     *
     * 优先返回预设金额，其次解析自定义金额输入。
     */
    fun getEffectiveAmount(): Double? {
        getSelectedAmount()?.let { return it }
        val custom = getCustomAmount().trim()
        return if (custom.isNotBlank()) custom.toDoubleOrNull() else null
    }
}