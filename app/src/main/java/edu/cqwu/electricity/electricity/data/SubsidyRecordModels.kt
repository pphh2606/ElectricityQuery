package edu.cqwu.electricity.electricity.data

import com.google.gson.annotations.SerializedName

/**
 * 单条补助记录（getRoomSubsidyData 响应项）
 *
 * 对应后端 `subsidyObj[]`：
 * - `subsidyTime`：补助发放时间 `yyyy-MM-dd HH:mm:ss`
 * - `feeType`：补助费用类型（0/20=电费, 1=水费, 2=气费, 3=热费）
 * - `quantity`：补助量（度数/立方）
 * - `amount`：补助金额（元）
 */
data class SubsidyRecord(
    @SerializedName("subsidyTime") val subsidyTime: String = "",
    @SerializedName("feeType") val feeType: Int = 0,
    @SerializedName("quantity") val quantity: Double = 0.0,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("orderNum") val orderNum: String = "",
    @SerializedName("roomName") val roomName: String = "",
    @SerializedName("roomId") val roomId: String = "",
)

/**
 * 补助记录 API 响应（getRoomSubsidyData）
 */
data class SubsidyRecordResponse(
    @SerializedName("ifSuccess") val ifSuccess: String = "",
    @SerializedName("resultMsg") val resultMsg: String? = null,
    @SerializedName("subsidyObj") val subsidyObj: List<SubsidyRecord>? = null,
)
