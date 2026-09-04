package edu.cqwu.electricity.electricity.data

import com.google.gson.annotations.SerializedName

/**
 * 单条用电记录（getRoomUsedData 响应项）
 *
 * 对应后端 `costObj[]`：
 * - `costTime`：时间标签（小时=日期时刻 / 每日=yyyy-MM-dd / 每月=yyyy-MM）
 * - `consumeTotal`：用量（度数）
 * - `costTotal`：金额（元）
 */
data class UsageRecordV2(
    @SerializedName("costTime") val costTime: String = "",
    @SerializedName("consumeTotal") val consumeTotal: Double = 0.0,
    @SerializedName("costTotal") val costTotal: Double = 0.0,
)

/**
 * 用电记录 API 响应（getRoomUsedData）
 */
data class UsageRecordV2Response(
    @SerializedName("ifSuccess") val ifSuccess: String = "",
    @SerializedName("resultMsg") val resultMsg: String? = null,
    @SerializedName("costObj") val costObj: List<UsageRecordV2>? = null,
)
