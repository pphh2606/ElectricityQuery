package edu.cqwu.electricity.data.network.cardrecharge

import com.google.gson.annotations.SerializedName

// ═══════════════════════════════════════════
//  数据模型 - 校园卡充值
// ═══════════════════════════════════════════

/**
 * 校园卡基本信息（queryBasicInfo 响应）
 */
data class CardBasicInfo(
    @SerializedName("username") val username: String,
    @SerializedName("idserial") val idserial: String,
    @SerializedName("projectId") val projectId: String,
    @SerializedName("status") val status: String,
    @SerializedName("maxBalance") val maxBalance: String,
    @SerializedName("expiredate") val expiredate: String,
) {
    /** 最大余额（元），从分转换 */
    val maxBalanceYuan: Double get() = maxBalance.toLongOrNull()?.div(100.0) ?: 1000.0

    /** 卡是否正常可用 */
    val isNormal: Boolean get() = status == "normal"
}

/**
 * 充值订单创建响应（createOrder → PayOrderTrade 字段）
 */
data class CardRechargeOrderResult(
    @SerializedName("id") val orderId: String,
    @SerializedName("orderNo") val orderNo: String,
    @SerializedName("amount") val amount: Long,
    @SerializedName("status") val status: String,
    @SerializedName("schdualCloseTime") val schdualCloseTime: String?,
    @SerializedName("productDesc") val productDesc: String?,
)

/**
 * 支付渠道（queryTradeChannel 响应）
 */
data class CardPaymentChannel(
    @SerializedName("id") val id: String,
    @SerializedName("channelName") val channelName: String,
    @SerializedName("code") val code: String,
    @SerializedName("imageUrl") val imageUrl: String?,
    @SerializedName("interfaceType") val interfaceType: String,
)

/**
 * 支付提交结果（toPayOrderTrade 响应）
 */
data class CardPaymentResult(
    @SerializedName("orderNo") val orderNo: String,
    @SerializedName("amount") val amount: Long,
    @SerializedName("sbHtml") val sbHtml: String?,
    @SerializedName("mwebUrl") val mwebUrl: String?,
    @SerializedName("payType") val payType: String,
    @SerializedName("tradeType") val tradeType: String,
)

/**
 * 订单状态（getOrdersSate 响应）
 */
data class CardOrderStatus(
    @SerializedName("id") val id: String,
    @SerializedName("orderNo") val orderNo: String,
    @SerializedName("status") val status: String,
    @SerializedName("amount") val amount: Long,
    @SerializedName("productDesc") val productDesc: String?,
    @SerializedName("schdualCloseTime") val schdualCloseTime: String?,
) {
    val amountYuan: Double get() = amount / 100.0

    val statusDisplay: String get() = when (status) {
        "COMPLETED" -> "已支付"
        "PENDING_PAYMENT" -> "待支付"
        "CLOSED" -> "已关闭"
        else -> status
    }
}

// ═══════════════════════════════════════════
//  API 响应包装（内部使用）
// ═══════════════════════════════════════════

internal data class CardBasicInfoResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: CardBasicInfo?,
)

internal data class CardRechargeOrderResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: CardRechargeOrderData?,
)

internal data class CardRechargeOrderData(
    @SerializedName("PayOrderTrade") val payOrderTrade: CardRechargeOrderResult?,
)

internal data class TradeChannelResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: List<CardPaymentChannel>?,
)

internal data class CardPaymentResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: CardPaymentResult?,
)

internal data class CardOrderStatusResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: CardOrderStatus?,
)

internal data class ConfigKeyResponse(
    @SerializedName("messageCode") val messageCode: String,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: String?,
)
