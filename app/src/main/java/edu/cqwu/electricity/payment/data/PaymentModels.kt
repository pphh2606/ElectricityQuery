package edu.cqwu.electricity.payment.data

import androidx.annotation.StringRes
import edu.cqwu.electricity.R

// ==================== 支付相关模型 ====================

/**
 * 支付方式枚举
 */
enum class PaymentMethod(val payType: String, @StringRes val labelRes: Int) {
    WECHAT("02", R.string.payment_channel_wechat),
    ALIPAY("01", R.string.payment_channel_alipay)
}

