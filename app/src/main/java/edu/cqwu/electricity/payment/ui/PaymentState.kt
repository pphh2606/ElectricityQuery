package edu.cqwu.electricity.payment.ui

import edu.cqwu.electricity.payment.data.PaymentMethod

/**
 * 支付流程共享状态
 *
 * 电费充值和校园卡充值共用，统一管理支付方式选择、支付提交、订单轮询等状态。
 */
data class PaymentState(
    /** 选中的支付方式 */
    val selectedMethod: PaymentMethod? = null,
    /** 支付提交中（gotToPay / toPayOrderTrade 调用中） */
    val isProcessing: Boolean = false,
    /** 支付错误信息 */
    val error: String? = null,
    /** 支付宝自动提交表单 HTML */
    val sbHtml: String? = null,
    /** 微信 H5 支付页 URL */
    val mwebUrl: String? = null,
    /** 订单状态（"COMPLETED" 表示支付完成） */
    val orderStatus: String? = null,
    /** 是否正在轮询订单状态 */
    val isPolling: Boolean = false,
)