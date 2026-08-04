package edu.cqwu.electricity.payment.data

import com.google.gson.annotations.SerializedName

// ==================== 支付相关模型 ====================

/**
 * 支付方式枚举
 */
enum class PaymentMethod(val payType: String, val displayName: String) {
    WECHAT("02", "微信支付"),
    ALIPAY("01", "支付宝")
}

