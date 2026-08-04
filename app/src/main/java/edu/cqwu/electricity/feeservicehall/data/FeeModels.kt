package edu.cqwu.electricity.feeservicehall.data

import com.google.gson.annotations.SerializedName

// ═══════════════════════════════════════════
//  数据模型 - 缴费项目
// ═══════════════════════════════════════════

data class FeeCategory(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("children") val children: List<FeeItem>?,
)

data class FeeItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String,
    @SerializedName("imgUrl") val imgUrl: String?,
    @SerializedName("proModelUrl") val proModelUrl: String?,
)

// ═══════════════════════════════════════════
//  通用 API 响应解析器
// ═══════════════════════════════════════════

/**
 * API 业务异常：服务端返回 [messageCode] != "0"
 */
class ApiBusinessException(message: String) : Exception(message)

// ═══════════════════════════════════════════
//  数据模型 - 订单
// ═══════════════════════════════════════════

data class OrderRecord(
    @SerializedName("id") val id: String,
    @SerializedName("orderNo") val orderNo: String,
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("productDesc") val productDesc: String?,
    @SerializedName("amount") val amount: Long, // 单位：分
    @SerializedName("status") val status: String,
    @SerializedName("displayStatus") val displayStatus: String?,
    @SerializedName("createDate") val createDate: String?,
    @SerializedName("actualCloseTime") val actualCloseTime: String?,
    @SerializedName("imgUrl") val imgUrl: String?,
    @SerializedName("proModelUrl") val proModelUrl: String?,
    @SerializedName("tradeChannel") val tradeChannel: String?,
    @SerializedName("schdualCloseTime") val schdualCloseTime: String?,
    @SerializedName("updateDate") val updateDate: String?,
    @SerializedName("balanceOrderTradeOrderNo") val balanceOrderTradeOrderNo: String?,
    @SerializedName("partnerId") val partnerId: String?,
    @SerializedName("engName") val engName: String?,
    @SerializedName("projectId") val projectId: String?,
) {
    /** 金额（单位：元），从分的转换 */
    val amountYuan: Double get() = amount / 100.0

    /** 状态显示文本 */
    val statusDisplay: String get() = when (status) {
        "COMPLETED" -> "已支付"
        "PENDING", "PENDING_PAYMENT" -> "待支付"
        "REFUND" -> "已退款"
        "CLOSED" -> "已关闭"
        else -> status
    }

    /** 是否为待支付状态 */
    val isPendingPayment: Boolean get() = status == "PENDING_PAYMENT"

    /** 支付渠道显示文本 */
    val tradeChannelDisplay: String get() = when (tradeChannel) {
        "01" -> "支付宝"
        "02" -> "微信支付"
        else -> tradeChannel ?: "未知"
    }
}

data class OrderPageData(
    @SerializedName("records") val records: List<OrderRecord>,
    @SerializedName("current") val current: Int?,
    @SerializedName("pages") val pages: Int?,
    @SerializedName("total") val total: Long?,
)

// ═══════════════════════════════════════════
//  数据模型 - 个人资料
// ═══════════════════════════════════════════

data class UserProfile(
    @SerializedName("name") val name: String?,
    @SerializedName("deptName") val deptName: String?,
    @SerializedName("accountNum") val accountNum: String?,
)
